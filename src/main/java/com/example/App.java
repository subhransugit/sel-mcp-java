package com.yourorg.qa.mcp;

import io.javalin.Javalin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;

public class App {
    public static void main(String[] args) {
        var app = Javalin.create().start(7030);
        var mapper = new ObjectMapper();

        app.post("/tool", ctx -> {
            var node = mapper.readTree(ctx.body());
            String tool = node.get("tool").asText();
            var input = node.has("input") && !node.get("input").isNull() ? node.get("input") : mapper.createObjectNode();

            switch (tool) {
                case "write_files": {
                    var projectRoot = input.get("projectRoot").asText();
                    Files.createDirectories(Path.of(projectRoot));
                    var files = input.get("files");
                    for (var f : files) {
                        var p = Path.of(projectRoot, f.get("path").asText());
                        Files.createDirectories(p.getParent());
                        Files.writeString(p, f.get("content").asText());
                    }
                    // add minimal Gradle skeleton if missing
                    Path build = Path.of(projectRoot, "build.gradle");
                    if (!Files.exists(build)) {
                        Files.writeString(build, "plugins { id 'java' }\\nrepositories { mavenCentral() }\\ndependencies { testImplementation 'org.testng:testng:7.10.2' }\\ntest { useTestNG() }\\n");
                        Files.writeString(Path.of(projectRoot, "settings.gradle"), "rootProject.name='tests-java'\\n");
                    }
                    ctx.json(Map.of("ok", true)); return;
                }
                case "run_gradle_tests": {
                    var projectRoot = input.get("projectRoot").asText();
                    var gradlew = Path.of(projectRoot, (isWindows()? "gradlew.bat":"gradlew")).toFile();
                    ProcessBuilder pb;
                    if (gradlew.exists()) pb = new ProcessBuilder(gradlew.getAbsolutePath(), "test");
                    else pb = new ProcessBuilder(isWindows()? "cmd":"gradle", isWindows()? "/c":"test");
                    pb.directory(new java.io.File(projectRoot));
                    pb.redirectErrorStream(true);
                    var proc = pb.start();
                    String out = new String(proc.getInputStream().readAllBytes());
                    int code = proc.waitFor();
                    ObjectNode resp = mapper.createObjectNode();
                    resp.put("code", code); resp.put("stdout", out); resp.put("stderr","");
                    ctx.result(resp.toString()); return;
                }
                default: {
                    ctx.status(400).json(Map.of("error", "unknown tool "+tool));
                }
            }
        });
    }
    static boolean isWindows(){ return System.getProperty("os.name").toLowerCase().contains("win"); }
}
