package com.myy.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(UserContextUtil.class)
public class CommonAutoConfiguration {
}
