package id.web.saka.report.product;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.web.saka.report.category.CategoryService;
import id.web.saka.report.category.colour.Colour;
import id.web.saka.report.category.colour.ColourService;
import id.web.saka.report.category.theme.Theme;
import id.web.saka.report.category.theme.ThemeService;
import id.web.saka.report.sap.SapStatus;
import id.web.saka.report.sap.SapStatusRepository;
import id.web.saka.report.util.Env;
import id.web.saka.report.util.Util;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ProductService {

    private static Logger LOG = (Logger) LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ColourService colourService;

    @Autowired
    private ThemeService themeService;

    @Autowired
    private SapStatusRepository sapStatusRepository;

    @Autowired
    private Env env;

    public boolean saveMasterProduct(String brand, String requestBody) throws JsonProcessingException {
        boolean isSaveSuccess = false;
        ObjectMapper objectMapper; objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;

        jsonNode = objectMapper.readTree(requestBody).get("payload");
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Product product = objectMapper.convertValue(jsonNode, Product.class);

        if(product != null) {
            product.setStatusEnum(ProductStatus.NEW);

            productRepository.saveAll(getMasterProductDetailGinee(brand, product));
            isSaveSuccess = true;
        }

        return isSaveSuccess;
    }

    private List<Product> getMasterProductDetailGinee(String brand, Product product) throws JsonProcessingException {
        ObjectMapper objectMapper; objectMapper = new ObjectMapper();
        RestTemplate restTemplate; restTemplate = new RestTemplate();

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String url = "https://api.ginee.com/openapi/product/master/v1/get?productId="+product.getId();

        HttpHeaders headers; headers = new HttpHeaders();
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.set("X-Advai-Country", "ID");
        headers.set("Authorization", Util.buildGineeSignatureErigo(brand, HttpMethod.GET, "/openapi/product/master/v1/get"));

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);

        JsonNode jsonNode = objectMapper.readTree(response.getBody()).get("data");
        product = objectMapper.convertValue(jsonNode, Product.class);

        Colour colour = colourService.getColourId(product.getColour());

        product.setBrand(brand);
        product.setSpu(categoryService.getCategoryGinee(objectMapper.readTree(response.getBody()).get("data").get("fullCategoryId"), objectMapper.readTree(response.getBody()).get("data").get("fullCategoryName")));
        product.setStatusEnum(ProductStatus.NEW);
        product.setColour(colour.getName());
        product.setColourId(colour.getCode());

        Theme theme = themeService.getThemeByThemeIdNameAndCategoryLevel1(product.getTheme(), product.getName(), categoryService.getCategoryLevel1Name(product));
        product.setThemeId(theme.getCode());
        product.setTheme(theme.getName());

        return getMasterVariantsProductDetailGinee(product, jsonNode.get("variations"));
    }

    private List<Product>  getMasterVariantsProductDetailGinee(Product product, JsonNode variations) {
        Product productVariant = null;
        List<Product> variantProducts = new ArrayList<>();

        if(variations.isArray()) {
            Iterator<JsonNode> jsonNodeIterator = variations.iterator();
            while (jsonNodeIterator.hasNext()) {
                JsonNode jsonNode = jsonNodeIterator.next();
                productVariant = new Product();

                productVariant.setBrand(product.getBrand());
                productVariant.setSpu(product.getSpu());
                productVariant.setName(product.getName());
                productVariant.setStatusEnum(ProductStatus.NEW);
                productVariant.setMsku(jsonNode.get("sku").asText());
                productVariant.setId(jsonNode.get("id").asText());
                productVariant.setPurchasePrice(jsonNode.get("purchasePrice").asLong());
                productVariant.setSellingPrice(jsonNode.get("sellingPrice").get("amount").asLong());
                productVariant.setColourId(product.getColourId());
                productVariant.setColour(product.getColour());
                productVariant.setTheme(productVariant.getTheme());
                productVariant.setThemeId(product.getThemeId());
                productVariant.setCreateDatetime(product.getCreateDatetime());
                productVariant.setUpdateDatetime(product.getUpdateDatetime());
                productVariant.setVariant(Util.arrayJsonNodetoString(jsonNode.get("optionValues")));

                variantProducts.add(productVariant);
            }
        }

        return variantProducts;
    }

    public void setSaveSAPMasterProduct(String brand, String status) throws Exception {
        List<Product> productSpus = productRepository.findByStatusGroupBySpu(brand, status);

        ObjectMapper objectMapper; objectMapper = new ObjectMapper();
        RestTemplate restTemplate; restTemplate = new RestTemplate();

        HttpHeaders headers; headers = new HttpHeaders();
        headers.setBearerAuth(Util.buildTokenSignatureSAP());
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        for(Product productSpu : productSpus) {
            JSONObject productSAPJsonObject = new JSONObject();
            Map<String, Object> invarticle = new HashMap<>();
            invarticle.put("BrandKey", "IGO");
            invarticle.put("BrandName", productSpu.getBrand());
            invarticle.put("categoryInit", themeService.changeThemeIdToString(productSpu.getThemeId()));
            invarticle.put("categoryName", themeService.getThemeId(productSpu.getThemeId()).getName());
            invarticle.put("TypeInit", productSpu.getThemeId()); //TODO : Need checks Type Init and Type Name Init in SAP Database
            invarticle.put("TypeName", categoryService.getCategoryLevel1Name(productSpu));
            invarticle.put("articleCode", productSpu.getSpu());
            invarticle.put("articleName", productSpu.getName());
            invarticle.put("colourInit", productSpu.getColourId());
            invarticle.put("colourName", colourService.getColourName(productSpu.getColourId()).getName());
            invarticle.put("sex", "U");
            invarticle.put("basePrice", productSpu.getPurchasePrice()+"");
            invarticle.put("salePrice", productSpu.getSellingPrice()+"");
            invarticle.put("notes", "-");
            invarticle.put("itemgroup", 101);
            //invarticle.put("code_CatType", productSpu.getCategoryId()); //TODO : Need checks code_catType

            List<Product> productMsku = productRepository.findBySpu(productSpu.getSpu());

            for(Product product : productMsku) {
                Map<String, Object> inventory = new HashMap<>();
                inventory.put("articleCode", product.getSpu());
                inventory.put("barcode", product.getMsku());
                inventory.put("sizes", product.getVariant());
                inventory.put("CurrentBasePrice", product.getPurchasePrice()+"");
                inventory.put("CurrentSalePrice", product.getSellingPrice()+"");
                inventory.put("qty", 0);
                inventory.put("CurrentSalePrice_validated", product.getSellingPrice());

                productSAPJsonObject.append("inventory", inventory);
            }

            productSAPJsonObject.put("invarticle", new Map[]{invarticle});

            HttpEntity<String> request = new HttpEntity<String>(productSAPJsonObject.toString(), headers);
            String orderResultAsJsonStr = restTemplate.postForObject("http://"+env.getSapApiKeyIp()+"/api/Master/Item", request, String.class);

            String jsonResponseStatus = objectMapper.readTree(orderResultAsJsonStr).get("status").asText();

            if(jsonResponseStatus.equals("SUCCEED")) {
                JsonNode jsonNodeSapStatusList = objectMapper.readTree(orderResultAsJsonStr).get("data");

                if(jsonNodeSapStatusList==null) {
                    return;
                }

                List<SapStatus> sapStatuses = jsonNodeSapStatusList.isArray()? Arrays.stream(objectMapper.convertValue(jsonNodeSapStatusList, SapStatus[].class)).toList():null;

                sapStatusRepository.saveAll(sapStatuses);

                for(id.web.saka.report.sap.SapStatus sapStatus : sapStatuses) {
                    if(sapStatus.getStatus().equals("SUCCEED")) {
                        productRepository.updateStatusByMsku(ProductStatus.UPDATE.toString(), sapStatus.getMsku());
                    }
                }
            }

        }

    }

    public boolean saveMasterProductRevota(String brand, String requestBody) throws JsonProcessingException {
        LOG.info("saveMasterProductRevota|start");
        boolean isSaveSuccess = false;
        ObjectMapper objectMapper; objectMapper = new ObjectMapper();

        JsonNode jsonNode = objectMapper.readTree(requestBody).get("data");
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<Product> products = jsonNode.isArray()?  Arrays.stream(objectMapper.convertValue(jsonNode, Product[].class)).toList(): Collections.singletonList(objectMapper.convertValue(jsonNode, Product.class));  ;

        if(products != null && products.size() > 0) {
            int i = 0;

            for(Product product : products) {
                productRepository.save(getMasterProductDetailRevota(brand, product));

                if(i > 999) {
                    productRepository.flush();
                    LOG.info("saveMasterProductRevota|flushed");
                    i = 0;
                    isSaveSuccess = true;
                }

                LOG.info("saveMasterProductRevota|Saved="+product.toString());
                i++;
            }

        }

        return isSaveSuccess;
    }

    public boolean saveMasterProductCogs(String brand, String requestBody) throws JsonProcessingException {
        LOG.info("saveMasterProductCogs|start");
        boolean isSaveSuccess = false;
        ObjectMapper objectMapper; objectMapper = new ObjectMapper();

        JsonNode jsonNode = objectMapper.readTree(requestBody).get("data");
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<Product> products = jsonNode.isArray()?  Arrays.stream(objectMapper.convertValue(jsonNode, Product[].class)).toList(): Collections.singletonList(objectMapper.convertValue(jsonNode, Product.class));  ;

        if(products != null && products.size() > 0) {
            int i = 0;
            for(Product product : products) {
                productRepository.updatePurchasePrice(product.getMsku(), product.getPurchasePrice());

                if(i > 999) {
                    productRepository.flush();
                    LOG.info("saveMasterProductCogs|flushed");
                    isSaveSuccess = true;
                    i = 0;
                }

                LOG.info("saveMasterProductCogs|Saved="+product.toString());
                i++;
            }

        }

        return isSaveSuccess;
    }

    private Product getMasterProductDetailRevota(String brand, Product product) throws JsonProcessingException {
        Colour colour = colourService.getColourId(product.getColour());

        product.setId(product.getMsku());
        product.setStatusEnum(ProductStatus.NEW);
        product.setColourId(colour.getCode());
        product.setCreateDatetime(new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date()));
        product.setUpdateDatetime(new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date()));

        Theme theme = themeService.getThemeByThemeIdNameAndCategoryLevel1(product.getCategory(), product.getName(), product.getType());
        product.setThemeId(theme.getCode());
        product.setTheme(theme.getName());

        return product;
    }
}
