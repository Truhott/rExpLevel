package ru.truhot.rexplevel.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.truhot.rexplevel.manager.ConfigManager;
import ru.truhot.rexplevel.util.logger.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

public final class UpdateUtil {

    private final @NotNull Plugin plugin;
    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull String url =
            "https://api.github.com/repos/Truhott/rExpLevel/releases/latest";

    @Getter
    private volatile boolean updateAvailable;
    @Getter
    private volatile @Nullable String latestVersion;

    public UpdateUtil(
            @NotNull Plugin plugin,
            @NotNull SchedulerUtil scheduler,
            @NotNull ConfigManager config
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.config = config;
    }

    public void check() {
        scheduler.runAsync(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "rExpLevel-UpdateCheck")
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }
                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                String tagName = jsonObject.has("tag_name")
                        ? jsonObject.get("tag_name").getAsString()
                        : "Unknown";
                latestVersion = stripVersionPrefix(tagName);
                String currentVersion = stripVersionPrefix(plugin.getPluginMeta().getVersion());
                updateAvailable = needsUpdate(currentVersion, latestVersion);
                if (updateAvailable) {
                    Logger.warn("Вы используете устаревшую версию плагина (" + currentVersion + ")");
                    Logger.warn("Скачать новую версию можно тут - https://github.com/Truhott/rExpLevel/releases");
                    Logger.warn("Или используйте /" + config.commands().main() + " update для обновления.");
                } else {
                    Logger.info("Актуальная версия: " + currentVersion);
                }
            } catch (Exception exception) {
                Logger.error("Ошибка проверки обновления: " + exception.getMessage());
            }
        });
    }

    public void downloadUpdate(@NotNull String tagName, @NotNull CommandSender sender) {
        try {
            File updateDirectory = new File(plugin.getDataFolder().getParentFile(), "update");
            Files.createDirectories(updateDirectory.toPath());
            File targetJar = new File(updateDirectory, "rExpLevel.jar");

            String downloadUrl =
                    "https://github.com/Truhott/rExpLevel/releases/download/"
                            + tagName
                            + "/rExpLevel.jar";
            URL download = URI.create(downloadUrl).toURL();
            URLConnection connection = download.openConnection();
            connection.setRequestProperty("User-Agent", "rExpLevel-UpdateDownload");
            int fileSize = connection.getContentLength();
            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(targetJar)) {
                byte[] data = new byte[1024];
                int bytesRead;
                int totalBytesRead = 0;
                int lastPercentage = 0;
                while ((bytesRead = in.read(data, 0, 1024)) != -1) {
                    out.write(data, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (fileSize <= 0) {
                        continue;
                    }
                    int progress = (int) ((double) totalBytesRead / fileSize * 100);
                    if (progress >= lastPercentage + 20) {
                        lastPercentage = progress;
                        Logger.info(
                                "Загрузка обновления: " + progress + "% ("
                                        + (totalBytesRead / 1024) + "/"
                                        + (fileSize / 1024) + " KB)"
                        );
                    }
                }
            }
            String displayVersion = stripVersionPrefix(tagName);
            Logger.info("Обновление до версии " + displayVersion + " успешно загружено.");
            send(sender, config.formatCommandMessage("update.success", Map.of("version", displayVersion)));
            send(sender, config.getCommandMessage("update.restart"));
        } catch (Exception exception) {
            Logger.error("Ошибка при загрузке обновления: " + exception.getMessage());
            send(sender, config.getCommandMessage("update.failed"));
        }
    }

    public void update(@NotNull CommandSender sender) {
        scheduler.runAsync(() -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "rExpLevel-UpdateCheck")
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    send(sender, config.getCommandMessage("update.failed"));
                    return;
                }
                JsonObject jsonObject = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!jsonObject.has("tag_name")) {
                    send(sender, config.getCommandMessage("update.failed"));
                    return;
                }
                String tagName = jsonObject.get("tag_name").getAsString();
                String normalizedLatest = stripVersionPrefix(tagName);
                latestVersion = normalizedLatest;
                String currentVersion = stripVersionPrefix(plugin.getPluginMeta().getVersion());
                updateAvailable = needsUpdate(currentVersion, normalizedLatest);
                if (!updateAvailable) {
                    send(sender, config.formatCommandMessage(
                            "update.latest",
                            Map.of("version", currentVersion)
                    ));
                    return;
                }
                downloadUpdate(tagName, sender);
            } catch (Exception exception) {
                Logger.error("Ошибка проверки обновления: " + exception.getMessage());
                send(sender, config.getCommandMessage("update.failed"));
            }
        });
    }

    private void send(@NotNull CommandSender sender, @NotNull String message) {
        if (sender instanceof Player player) {
            scheduler.runFor(player, () -> sender.sendMessage(MessageUtil.parseText(message)));
            return;
        }
        if (sender instanceof Entity entity) {
            scheduler.runFor(entity, () -> sender.sendMessage(MessageUtil.parseText(message)));
            return;
        }
        scheduler.runGlobal(() -> sender.sendMessage(MessageUtil.parseText(message)));
    }

    private @NotNull String stripVersionPrefix(@NotNull String version) {
        if (version.startsWith("v") || version.startsWith("V")) {
            return version.substring(1);
        }
        return version;
    }

    private boolean needsUpdate(@NotNull String current, @NotNull String latest) {
        try {
            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");
            int length = Math.max(currentParts.length, latestParts.length);
            for (int index = 0; index < length; index++) {
                int currentValue = index < currentParts.length
                        ? Integer.parseInt(currentParts[index])
                        : 0;
                int latestValue = index < latestParts.length
                        ? Integer.parseInt(latestParts[index])
                        : 0;
                if (currentValue < latestValue) {
                    return true;
                }
                if (currentValue > latestValue) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
