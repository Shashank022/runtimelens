package io.github.shashank022.runtimelens.report;

import io.github.shashank022.runtimelens.engine.Analyzer.ScanResult;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.util.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SarifReporter implements Reporter {
    @Override
    public String render(ScanResult result) {
        Map<String, Finding> rules = new LinkedHashMap<>();
        for (Finding f : result.findings()) rules.putIfAbsent(f.ruleId(), f);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"$schema\": \"https://json.schemastore.org/sarif-2.1.0.json\",\n");
        sb.append("  \"version\": \"2.1.0\",\n");
        sb.append("  \"runs\": [{\n");
        sb.append("    \"tool\": {\"driver\": {\n");
        sb.append("      \"name\": \"RuntimeLens\",\n");
        sb.append("      \"version\": \"0.1.0\",\n");
        sb.append("      \"rules\": [\n");

        int r = 0;
        for (Finding f : rules.values()) {
            sb.append("        {");
            sb.append("\"id\":").append(JsonUtils.quote(f.ruleId())).append(",");
            sb.append("\"name\":").append(JsonUtils.quote(f.title().replace(" ", ""))).append(",");
            sb.append("\"shortDescription\":{\"text\":").append(JsonUtils.quote(f.title())).append("},");
            sb.append("\"fullDescription\":{\"text\":").append(JsonUtils.quote(f.message())).append("},");
            sb.append("\"defaultConfiguration\":{\"level\":").append(JsonUtils.quote(f.severity().sarifLevel())).append("}");
            sb.append("}");
            if (++r < rules.size()) sb.append(",");
            sb.append("\n");
        }

        sb.append("      ]\n");
        sb.append("    }},\n");
        sb.append("    \"results\": [\n");

        for (int i = 0; i < result.findings().size(); i++) {
            Finding f = result.findings().get(i);
            String uri = f.file().toString().replace('\\', '/');
            sb.append("      {\n");
            sb.append("        \"ruleId\": ").append(JsonUtils.quote(f.ruleId())).append(",\n");
            sb.append("        \"level\": ").append(JsonUtils.quote(f.severity().sarifLevel())).append(",\n");
            sb.append("        \"message\": {\"text\": ")
                    .append(JsonUtils.quote(f.message() + " Suggestion: " + f.suggestion())).append("},\n");
            sb.append("        \"locations\": [{\"physicalLocation\": {");
            sb.append("\"artifactLocation\": {\"uri\": ").append(JsonUtils.quote(uri)).append("},");
            sb.append("\"region\": {\"startLine\": ").append(f.line()).append("}");
            sb.append("}}]\n");
            sb.append("      }");
            if (i + 1 < result.findings().size()) sb.append(",");
            sb.append("\n");
        }

        sb.append("    ]\n");
        sb.append("  }]\n");
        sb.append("}\n");
        return sb.toString();
    }
}
