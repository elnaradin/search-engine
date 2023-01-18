package searchengine.dto.statistics;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Data implements Comparable{
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    @Override
    public int compareTo(Object o) {
        return Float.compare(getRelevance(),
                ((Data) o).getRelevance());
    }
}
