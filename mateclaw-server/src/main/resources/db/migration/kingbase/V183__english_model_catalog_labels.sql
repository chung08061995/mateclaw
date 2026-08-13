-- This distribution is English-only, but several legacy catalog migrations
-- seeded Chinese provider names and model descriptions. Update existing
-- installations without modifying already-applied Flyway migrations.

UPDATE mate_model_provider SET name = 'Alibaba Cloud Bailian Team Token Plan' WHERE provider_id = 'bailian-team';
UPDATE mate_model_provider SET name = 'Tencent Hunyuan 3D' WHERE provider_id = 'hunyuan-3d';
UPDATE mate_model_provider SET name = 'SiliconFlow (China)' WHERE provider_id = 'siliconflow-cn';
UPDATE mate_model_provider SET name = 'SiliconFlow (International)' WHERE provider_id = 'siliconflow-intl';

UPDATE mate_model_config SET description = 'Latest flagship model' WHERE id = 1000000214;
UPDATE mate_model_config SET description = 'Zhipu Coding Plan — GLM-5.2 latest flagship' WHERE id = 1000000238;
UPDATE mate_model_config SET description = 'Bailian Team Plan — Qwen flagship reasoning model with vision understanding and text generation' WHERE id = 1000000400;
UPDATE mate_model_config SET description = 'Bailian Team Plan — latest DeepSeek reasoning model' WHERE id = 1000000401;
UPDATE mate_model_config SET description = 'Bailian Team Plan — Zhipu GLM-5 text generation model' WHERE id = 1000000402;
UPDATE mate_model_config SET description = 'Bailian Team Plan — Qwen image generation model' WHERE id = 1000000403;
UPDATE mate_model_config SET description = 'Bailian Team Plan — flagship Qwen image generation model' WHERE id = 1000000404;
UPDATE mate_model_config SET description = 'Bailian Team Plan — Wan image generation model' WHERE id = 1000000405;
UPDATE mate_model_config SET description = 'Bailian Team Plan — flagship Wan image generation model' WHERE id = 1000000406;
UPDATE mate_model_config SET description = 'Tencent Hunyuan 3D 3.1 — highest-quality model with PBR, multi-view, and geometry-only output options' WHERE id = 1000000500;
UPDATE mate_model_config SET description = 'Tencent Hunyuan 3D 3.0 — previous-generation Pro model using the SubmitHunyuanTo3DProJob API' WHERE id = 1000000501;
UPDATE mate_model_config SET description = 'Tencent Hunyuan 3D Express — fastest model using the SubmitHunyuanTo3DRapidJob API; supports Prompt and ImageUrl inputs only' WHERE id = 1000000502;
UPDATE mate_model_config SET description = 'SiliconFlow — cost-efficient Qwen3 MoE model' WHERE id = 1000000503;
UPDATE mate_model_config SET description = 'SiliconFlow — Zhipu GLM-4 9B with a free tier' WHERE id = 1000000504;
UPDATE mate_model_config SET description = 'SiliconFlow Pro — priority-routed DeepSeek V3 model' WHERE id = 1000000505;
UPDATE mate_model_config SET description = 'SiliconFlow Pro — priority-routed DeepSeek R1 reasoning model' WHERE id = 1000000506;
UPDATE mate_model_config SET description = 'OpenCode free model — Big Pickle' WHERE id = 1000000520;
UPDATE mate_model_config SET description = 'OpenCode free model — Nemotron 3 Super' WHERE id = 1000000521;
UPDATE mate_model_config SET description = 'DashScope Qwen text embedding v3 (1,024 dimensions)' WHERE id = 1000001001;
UPDATE mate_model_config SET description = 'DashScope Qwen text embedding v2 (1,536 dimensions)' WHERE id = 1000001002;
