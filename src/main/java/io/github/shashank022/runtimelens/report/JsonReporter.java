package io.github.shashank022.runtimelens.report;

import io.github.shashank022.runtimelens.engine.Analyzer.ScanResult;
import io.github.shashank022.runtimelens.model.Finding;
import io.github.shashank022.runtimelens.util.JsonUtils;

public final class JsonReporter implements Reporter {
    @Override
    public String render(ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"tool\": \"RuntimeLens\",\n");
        sb.append("  \"version\": \"0.1.0\",\n");
        sb.append("  \"root\": ").append(JsonUtils.quote(result.root().toString())).append(",\n");
        sb.append("  \"filesScanned\": ").append(result.filesScanned()).append(",\n");
        sb.append("  \"analysisErrors\": ").append(result.analysisErrors()).append(",\n");
        sb.append("  \"findings\": [\n");
        for (int i = 0; i < result.findings().size(); i++) {
            Finding f = result.findings().get(i);
            sb.append("    {");
            sb.append("\"ruleId\":").append(JsonUtils.quote(f.ruleId())).append(",");
            sb.append("\"title\":").append(JsonUtils.quote(f.title())).append(",");
            sb.append("\"severity\":").append(JsonUtils.quote(f.severity().name())).append(",");
            sb.append("\"category\":").append(JsonUtils.quote(f.category())).append(",");
            sb.append("\"file\":").append(JsonUtils.quote(f.file().toString().replace('\\', '/'))).append(",");
            sb.append("\"line\":").append(f.line()).append(",");
            sb.append("\"message\":").append(JsonUtils.quote(f.message())).append(",");
            sb.append("\"evidence\":").append(JsonUtils.quote(f.evidence())).append(",");
            sb.append("\"suggestion\":").append(JsonUtils.quote(f.suggestion()));
            sb.append("}");
            if (i + 1 < result.findings().size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }
}
