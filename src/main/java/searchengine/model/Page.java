package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;


@Entity
@Getter
@Setter
@Table(name = "pages")
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT", nullable = false)
    private int id;

    @JoinColumn(name = "site_id")
    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    private Site site;

    @Column(columnDefinition = "TEXT NOT NULL, INDEX (path(150))")
    private String path;

    @Column(columnDefinition = "INT", nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT CHARACTER SET " +
            "utf8mb4 COLLATE utf8mb4_general_ci",
            nullable = false)
    private String content;
}

