package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPELIST_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String shopTypeStr = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPELIST_KEY);
        if (StrUtil.isNotBlank(shopTypeStr)) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeStr, ShopType.class);
            return Result.ok(shopTypes);
        }
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if(typeList.isEmpty()) {
            return Result.fail("没有商户类型！");
        }
        String toJsonStr = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPELIST_KEY, toJsonStr);
        return Result.ok(typeList);
    }
}
