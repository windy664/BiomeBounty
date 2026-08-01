package com.biomebounty.core.scanner;

import com.biomebounty.api.BiomeData;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Jar 文件扫描器
 */
public class JarScanner {

    private static final Pattern BIOME_PATTERN =
            Pattern.compile("data/([a-z0-9_.-]+)/worldgen/biome/([a-z0-9_./-]+)\\.json");

    /**
     * 扫描服务器目录
     * @param worldDirs 额外扫描的世界目录列表（如 Bukkit.getWorlds() 的 world container）
     */
    public Map<String, BiomeData> scanServer(Path serverDir, BiomeParser parser, Path... worldDirs) {
        Map<String, BiomeData> result = new HashMap<>();

        // 扫描服务器 jar
        Path serverJar = findServerJar(serverDir);
        if (serverJar != null) {
            scanJar(serverJar, parser, result);
        }

        // Paper 的 vanilla jar 在 libraries/ 目录下，包含完整的原版 worldgen 数据
        scanLibraries(serverDir, parser, result);

        // 扫描 mods/ 和 plugins/ 目录下的 jar
        Path modsDir = serverDir.resolve("mods");
        if (Files.exists(modsDir)) {
            scanDirectory(modsDir, parser, result);
        }

        Path pluginsDir = serverDir.resolve("plugins");
        if (Files.exists(pluginsDir)) {
            scanDirectory(pluginsDir, parser, result);
        }

        // 扫描世界的 datapacks 目录（自定义群系包）
        for (Path worldDir : worldDirs) {
            scanDatapacks(worldDir, parser, result);
        }

        return result;
    }

    /**
     * 扫描 Paper 的 libraries 目录
     * Paper 把 vanilla jar 放在 libraries/net/minecraft/server/<version>/ 下
     * 这个 jar 包含完整的 data/worldgen/ 数据
     */
    private void scanLibraries(Path serverDir, BiomeParser parser, Map<String, BiomeData> result) {
        Path libraries = serverDir.resolve("libraries");
        if (!Files.exists(libraries)) return;

        // 递归扫描 libraries 下所有 jar（包括 vanilla jar、mod 依赖等）
        try (Stream<Path> jars = Files.walk(libraries)) {
            jars.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jarPath -> scanJar(jarPath, parser, result));
        } catch (IOException e) { /* ignore */ }
    }

    /**
     * 扫描世界 datapacks 目录中的 zip/jar 群系包
     */
    private void scanDatapacks(Path worldDir, BiomeParser parser, Map<String, BiomeData> result) {
        Path datapacks = worldDir.resolve("datapacks");
        if (!Files.exists(datapacks)) return;

        // 扫描 datapacks 下的 zip 文件
        scanDirectory(datapacks, parser, result);

        // 扫描 datapacks 下的目录（展开的包）
        try (DirectoryStream<Path> packs = Files.newDirectoryStream(datapacks)) {
            for (Path packDir : packs) {
                if (Files.isDirectory(packDir)) {
                    scanPackDirectory(packDir, parser, result);
                }
            }
        } catch (IOException e) { /* ignore */ }
    }

    /**
     * 扫描展开的数据包目录（非压缩）
     */
    private void scanPackDirectory(Path packDir, BiomeParser parser, Map<String, BiomeData> result) {
        Path dataDir = packDir.resolve("data");
        if (!Files.exists(dataDir)) return;

        try (Stream<Path> namespaces = Files.list(dataDir)) {
            namespaces.filter(Files::isDirectory).forEach(nsDir -> {
                String namespace = nsDir.getFileName().toString();
                Path biomeDir = nsDir.resolve("worldgen").resolve("biome");
                if (!Files.exists(biomeDir)) return;

                try (Stream<Path> biomeFiles = Files.walk(biomeDir)) {
                    biomeFiles.filter(p -> p.toString().endsWith(".json")).forEach(biomeFile -> {
                        try {
                            String relative = biomeDir.relativize(biomeFile).toString();
                            String biomeName = relative.replace('\\', '/').replace(".json", "");
                            String id = namespace + ":" + biomeName;

                            BiomeData data = parser.parseBiomeFile(biomeFile, namespace, biomeName);
                            if (data != null) {
                                result.put(id, data);
                            }
                        } catch (Exception e) { /* skip */ }
                    });
                } catch (IOException e) { /* skip */ }
            });
        } catch (IOException e) { /* skip */ }
    }

    private Path findServerJar(Path serverDir) {
        String[] names = {"server.jar", "minecraft_server.jar", "spigot.jar", "paper.jar"};
        for (String name : names) {
            Path path = serverDir.resolve(name);
            if (Files.exists(path)) return path;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(serverDir, "*.jar")) {
            for (Path path : stream) return path;
        } catch (IOException e) { /* ignore */ }

        return null;
    }

    private void scanDirectory(Path dir, BiomeParser parser, Map<String, BiomeData> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jarPath : stream) {
                scanJar(jarPath, parser, result);
            }
        } catch (IOException e) { /* ignore */ }
    }

    private void scanJar(Path jarPath, BiomeParser parser, Map<String, BiomeData> result) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream()
                    .map(ZipEntry::getName)
                    .map(BIOME_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .forEach(m -> {
                        String namespace = m.group(1);
                        String biomeName = m.group(2);
                        String id = namespace + ":" + biomeName;

                        BiomeData data = parser.parseBiome(jar, m.group(0), namespace, biomeName);
                        if (data != null) {
                            result.put(id, data);
                        }
                    });
        } catch (IOException e) { /* ignore */ }
    }
}
