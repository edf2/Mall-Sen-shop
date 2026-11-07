package com.mallsen.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPredictionMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private LocalDateTime time;
    private List<Map<String, Object>> predictions;
    private int warningCount;
}
