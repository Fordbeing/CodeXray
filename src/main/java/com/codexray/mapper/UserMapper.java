package com.codexray.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codexray.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
