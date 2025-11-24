package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "exceptions")
@Getter
@Setter
public class ShiftException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "custom_code")
    private String customCode;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "anniversary_memo", columnDefinition = "text")
    private String anniversaryMemo;

    @Column(name = "repeat_yearly")
    private boolean repeatYearly;
}
