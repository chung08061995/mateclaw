package vip.mate.llm.account.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import vip.mate.llm.account.model.ProviderAccountEntity;

@Mapper
public interface ProviderAccountMapper extends BaseMapper<ProviderAccountEntity> {

    /** Atomic cumulative usage update so concurrent chat completions cannot lose tokens. */
    @Update("""
            UPDATE mate_provider_account
               SET prompt_tokens = COALESCE(prompt_tokens, 0) + #{prompt},
                   completion_tokens = COALESCE(completion_tokens, 0) + #{completion},
                   total_tokens = COALESCE(total_tokens, 0) + #{total},
                   last_success_at = #{now},
                   last_used_at = #{now},
                   last_error_code = NULL,
                   last_error_message = NULL,
                   status = CASE
                       WHEN status IN ('ACTIVE', 'EXHAUSTED', 'COOLDOWN') THEN status
                       ELSE 'AVAILABLE'
                   END,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id}
            """)
    int recordSuccess(@Param("id") Long id,
                      @Param("prompt") long prompt,
                      @Param("completion") long completion,
                      @Param("total") long total,
                      @Param("now") long now);
}
