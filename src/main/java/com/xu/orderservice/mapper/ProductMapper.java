package com.xu.orderservice.mapper;

import com.xu.orderservice.dto.InventoryDto;
import com.xu.orderservice.dto.ProductDto;
import com.xu.orderservice.entity.Product;
import com.xu.orderservice.entity.ProductInventory;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductDto toDto(Product entity);

    InventoryDto toDto(ProductInventory inv);
}
