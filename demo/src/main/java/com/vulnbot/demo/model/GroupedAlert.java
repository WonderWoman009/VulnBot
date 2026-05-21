// model/GroupedAlert.java
package com.vulnbot.demo.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class GroupedAlert {

    // Grouping key — same package + severity across repos
    private String packageName;
    private String severity;
    private String cveId;
    private String summary;
    private String safeVersion;
    private String vulnerableRange;

    // Which repos are affected
    private List<String> affectedRepos;

    // Original alert URLs per repo for reference
    private List<String> alertUrls;

    // Alert numbers per repo
    private List<Integer> alertNumbers;

    public String getGroupingKey() {
        return packageName + "_" + severity;
    }

    public String getAffectedReposSummary() {
        return String.join(", ", affectedRepos);
    }
}