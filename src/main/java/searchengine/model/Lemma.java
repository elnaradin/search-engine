package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "lemmas")
public class Lemma implements Comparable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT", nullable = false)
    private int id;

    @JoinTable(name = "lemmas_sites", joinColumns = {@JoinColumn(name = "lemma_id")},
            inverseJoinColumns = {@JoinColumn(name = "site_id")})
    @ManyToMany(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    private Set<Site> sites = new HashSet<>();

    @Column(nullable = false, unique = true)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @Override
    public int compareTo(Object o) {
        return Float.compare(getFrequency(),
                ((Lemma) o).getFrequency());
    }
}