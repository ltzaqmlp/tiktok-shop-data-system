package com.company.ops.admin;

import com.company.ops.common.Api;
import com.company.ops.common.Db;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.company.ops.common.Db.p;

@RestController
@RequestMapping("/api/v1/admin/product-sku-mapping")
public class ProductSkuMappingController {
    private final Db db;

    public ProductSkuMappingController(Db db) { this.db = db; }

    @GetMapping
    public Object list(@RequestParam long shopId, HttpServletRequest req) {
        return Api.ok(req, db.rows("select id,shop_id,product_id,display_name,seller_sku,enabled,sort_order from product_sku_mapping where shop_id=#{p.shop} order by product_id,sort_order,id", p("shop", shopId)));
    }

    @PostMapping
    public Object create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        long shop = Api.idValue(body.get("shopId"));
        Api.require(db.count("select count(*) from dim_shop where id=#{p.shop} and enabled", p("shop", shop)) == 1, "店铺无效");
        String productId = Api.text(body, "productId", 64, true).trim();
        String displayName = Api.text(body, "displayName", 100, true).trim();
        String sellerSku = Api.text(body, "sellerSku", 100, true).trim();
        Api.require(!productId.isBlank() && !displayName.isBlank() && !sellerSku.isBlank(), "Product ID、展示名称和 Seller SKU 不能为空");
        String id = db.insert("insert into product_sku_mapping(shop_id,product_id,display_name,seller_sku,enabled,sort_order) values(#{p.shop},#{p.product},#{p.name},#{p.sku},#{p.enabled},#{p.sort})", p("shop", shop, "product", productId, "name", displayName, "sku", sellerSku, "enabled", !Boolean.FALSE.equals(body.get("enabled")), "sort", sortOrder(body.getOrDefault("sortOrder", 0))));
        req.setAttribute("auditAction", "PRODUCT_SKU_MAPPING_CREATE");
        return Api.ok(req, p("id", id));
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        var old = db.one("select * from product_sku_mapping where id=#{p.id}", p("id", id));
        Api.require(!old.isEmpty(), "Product ID 映射不存在");
        String productId = Api.text(body, "productId", 64, true).trim();
        String displayName = Api.text(body, "displayName", 100, true).trim();
        String sellerSku = Api.text(body, "sellerSku", 100, true).trim();
        Api.require(!productId.isBlank() && !displayName.isBlank() && !sellerSku.isBlank(), "Product ID、展示名称和 Seller SKU 不能为空");
        db.exec("update product_sku_mapping set product_id=#{p.product},display_name=#{p.name},seller_sku=#{p.sku},enabled=#{p.enabled},sort_order=#{p.sort},updated_at=now() where id=#{p.id}", p("id", id, "product", productId, "name", displayName, "sku", sellerSku, "enabled", !Boolean.FALSE.equals(body.get("enabled")), "sort", sortOrder(body.getOrDefault("sortOrder", 0))));
        req.setAttribute("auditBefore", old);
        req.setAttribute("auditAfter", body);
        req.setAttribute("auditAction", "PRODUCT_SKU_MAPPING_UPDATE");
        return Api.ok(req, Map.of());
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable long id, HttpServletRequest req) {
        Api.require(db.exec("delete from product_sku_mapping where id=#{p.id}", p("id", id)) == 1, "Product ID 映射不存在");
        req.setAttribute("auditAction", "PRODUCT_SKU_MAPPING_DELETE");
        req.setAttribute("targetId", id);
        return Api.ok(req, Map.of());
    }

    private static int sortOrder(Object raw) {
        String value = String.valueOf(raw);
        Api.require(value.matches("\\d{1,9}"), "排序必须为非负整数");
        return Integer.parseInt(value);
    }
}
