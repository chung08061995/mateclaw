package vip.mate.llm.account.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mate_provider_account")
public class ProviderAccountEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String providerId;
    private String label;
    private String externalAccountId;
    private String authType;

    /** Complete credential payload encrypted by SettingCrypto (AES-256-GCM). */
    private String credentialJson;

    private Boolean enabled;
    private Integer priority;
    private String status;
    private String quotaStatus;
    private BigDecimal quotaUsedPercent;
    private Long quotaResetAt;
    private Long quotaUpdatedAt;
    private Boolean alertEnabled;
    private BigDecimal alertThresholdPercent;
    private String alertStatus;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long lastSuccessAt;
    private Long lastFailureAt;
    private Long lastUsedAt;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;

    /** TRUE only for the one credential lazily copied from mate_model_provider. */
    private Boolean legacyImport;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
