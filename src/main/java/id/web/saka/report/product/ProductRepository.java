package id.web.saka.report.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    @Query("SELECT s FROM Product s WHERE s.status =:status AND s.brand =:brand AND s.purchasePrice > 0  GROUP BY s.spu ")
    List<Product> findByStatusGroupBySpu(@Param("brand") String brand, @Param("status") String status);

    @Query("SELECT s FROM Product s WHERE s.spu =:spu AND s.purchasePrice > 0  ")
    List<Product> findBySpu(@Param("spu") String spu);

    @Transactional
    @Modifying
    @Query("UPDATE Product s SET s.status =:status WHERE s.msku =:msku ")
    void updateStatusByMsku(@Param("status") String status, @Param("msku") String msku);

}
