package com.myy.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从项目根目录的 .env 文件加载敏感配置（环境变量风格 KEY=VALUE），
 * 注入到 Environment，使 application.yml 中的 ${DB_PASS} 等占位符可解析。
 * <p>
 * 优先级：真实环境变量 &gt; .env 文件（.env 作为兜底，addLast 放在最后）。
 * .env 文件已加入 .gitignore，敏感信息不进入版本库。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String ENV_FILE = ".env";
    private static final String PROPERTY_SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = locateEnvFile();
        if (envFile == null) {
            return;
        }
        Map<String, Object> props = parse(envFile);
        if (!props.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }
    }

    /**
     * 从当前工作目录向上逐级查找 .env（兼容 IDEA 以模块目录为工作目录启动的场景）。
     */
    private Path locateEnvFile() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(ENV_FILE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private Map<String, Object> parse(Path file) {
        Map<String, Object> props = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int idx = trimmed.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                // 去掉可选的单/双引号包裹
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                props.put(key, value);
            }
        } catch (IOException ignored) {
            // .env 读取失败则回退到真实环境变量
        }
        return props;
    }
}
