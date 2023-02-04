package searchengine.dto.statistics;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Data implements Comparable<Data>{
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public int compareTo(Data o) {
        return Float.compare(getRelevance(),
                o.getRelevance());
    }
}
