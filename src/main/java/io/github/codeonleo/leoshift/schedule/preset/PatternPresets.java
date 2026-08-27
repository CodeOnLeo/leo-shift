package io.github.codeonleo.leoshift.schedule.preset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 근무 패턴 프리셋 모음. classpath의 JSON 리소스를 읽어들인다.
 *
 * <p>DB가 아니라 앱에 동봉한 리소스인 이유는, git으로 버전이 관리되고
 * 마이그레이션이 필요 없기 때문이다. 나중에 사용자 정의 프리셋 공유가 필요해지면
 * 그때 DB로 옮기면 된다.
 *
 * <p>불변이며 스레드 안전하다. 스프링에 의존하지 않는다.
 */
public final class PatternPresets {

    static final String DEFAULT_RESOURCE = "/presets/shift-patterns.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 기본 리소스는 한 번만 읽는다. */
    private static final class Holder {
        static final PatternPresets INSTANCE = fromResource(DEFAULT_RESOURCE);
    }

    private final int version;
    private final List<ScheduleTypeSpec> commonScheduleTypes;
    private final List<PatternPreset> presets;
    private final Map<String, PatternPreset> byId;

    private PatternPresets(int version, List<ScheduleTypeSpec> common, List<PatternPreset> presets) {
        this.version = version;
        this.commonScheduleTypes = List.copyOf(common);
        this.presets = List.copyOf(presets);

        Map<String, PatternPreset> index = new LinkedHashMap<>();
        for (PatternPreset preset : presets) {
            if (index.put(preset.id(), preset) != null) {
                throw new IllegalArgumentException("프리셋 id가 중복이다: " + preset.id());
            }
        }
        this.byId = Map.copyOf(index);
    }

    /** 앱에 동봉된 기본 프리셋. */
    public static PatternPresets load() {
        return Holder.INSTANCE;
    }

    public static PatternPresets fromResource(String resourcePath) {
        try (InputStream in = PatternPresets.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("프리셋 리소스를 찾을 수 없다: " + resourcePath);
            }
            return from(in);
        } catch (IOException e) {
            throw new UncheckedIOException("프리셋을 읽지 못했다: " + resourcePath, e);
        }
    }

    public static PatternPresets from(InputStream in) {
        try {
            return parse(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("프리셋 JSON을 파싱하지 못했다", e);
        }
    }

    // ------------------------------------------------------------------

    public List<PatternPreset> all() {
        return presets;
    }

    public Optional<PatternPreset> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public PatternPreset require(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException("없는 프리셋이다: " + id));
    }

    public List<PatternPreset> byCategory(PatternPreset.Category category) {
        return presets.stream().filter(p -> p.category() == category).toList();
    }

    /** 모든 캘린더에 공통으로 들어가는 타입. 연차·반차 등. */
    public List<ScheduleTypeSpec> commonScheduleTypes() {
        return commonScheduleTypes;
    }

    /**
     * 프리셋을 적용할 때 만들어야 할 일정 타입 전체.
     * 프리셋 고유 타입 + 공통 타입이고, 코드가 겹치면 프리셋 쪽이 이긴다.
     */
    public List<ScheduleTypeSpec> scheduleTypesFor(PatternPreset preset) {
        List<ScheduleTypeSpec> result = new ArrayList<>(preset.scheduleTypes());
        Set<String> codes = new LinkedHashSet<>();
        preset.scheduleTypes().forEach(spec -> codes.add(spec.code()));
        for (ScheduleTypeSpec common : commonScheduleTypes) {
            if (codes.add(common.code())) {
                result.add(common);
            }
        }
        return List.copyOf(result);
    }

    public int version() {
        return version;
    }

    public int size() {
        return presets.size();
    }

    // ------------------------------------------------------------------
    // 파싱
    // ------------------------------------------------------------------

    private static PatternPresets parse(JsonNode root) {
        int version = root.path("version").asInt(1);

        List<ScheduleTypeSpec> common = new ArrayList<>();
        for (JsonNode node : root.path("commonScheduleTypes")) {
            common.add(parseScheduleType(node));
        }

        JsonNode presetsNode = root.path("presets");
        if (!presetsNode.isArray() || presetsNode.isEmpty()) {
            throw new IllegalArgumentException("presets 배열이 비어 있다");
        }
        List<PatternPreset> presets = new ArrayList<>();
        for (JsonNode node : presetsNode) {
            presets.add(parsePreset(node));
        }
        return new PatternPresets(version, common, presets);
    }

    private static PatternPreset parsePreset(JsonNode node) {
        String id = text(node, "id");

        List<String> tags = new ArrayList<>();
        node.path("tags").forEach(tag -> tags.add(tag.asText()));

        List<String> sequence = new ArrayList<>();
        node.path("sequence").forEach(code -> sequence.add(code.asText()));

        List<ScheduleTypeSpec> types = new ArrayList<>();
        for (JsonNode typeNode : node.path("scheduleTypes")) {
            types.add(parseScheduleType(typeNode));
        }

        List<TeamOption> teams = new ArrayList<>();
        for (JsonNode teamNode : node.path("teams")) {
            teams.add(new TeamOption(text(teamNode, "label"), teamNode.path("offset").asInt()));
        }

        JsonNode hintNode = node.path("anchorHint");
        AnchorHint hint = hintNode.isMissingNode() || hintNode.isNull()
                ? null
                : new AnchorHint(text(hintNode, "code"), text(hintNode, "question"));

        JsonNode weekdayNode = node.path("anchorWeekday");
        DayOfWeek anchorWeekday = weekdayNode.isMissingNode() || weekdayNode.isNull()
                ? null
                : DayOfWeek.valueOf(weekdayNode.asText());

        try {
            return new PatternPreset(
                    id,
                    text(node, "name"),
                    PatternPreset.Category.valueOf(text(node, "category")),
                    tags,
                    node.path("description").asText(null),
                    anchorWeekday,
                    sequence,
                    types,
                    teams,
                    hint);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("프리셋이 잘못됐다 [" + id + "]: " + e.getMessage(), e);
        }
    }

    private static ScheduleTypeSpec parseScheduleType(JsonNode node) {
        return new ScheduleTypeSpec(
                text(node, "code"),
                text(node, "name"),
                node.path("color").asText("#94A3B8"),
                ScheduleTypeSpec.Category.valueOf(text(node, "category")),
                time(node, "startTime"),
                time(node, "endTime"),
                node.path("crossesMidnight").asBoolean(false),
                node.path("halfDay").asBoolean(false));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("필수 항목이 없다: " + field);
        }
        return value.asText();
    }

    private static LocalTime time(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : LocalTime.parse(value.asText());
    }
}
