# Model Router Tool

`select_model.py` chooses the lowest estimated-cost available model that satisfies an explicit task capability, effort level, and context requirement.

The tool is data-driven. Update `models.json` whenever Copilot model availability, pricing, context windows, or capabilities change. Pricing values are estimates in USD per one million tokens, not a substitute for current Copilot billing information.

Example:

```bash
python3 .github/tools/model-router/select_model.py \
  --task security \
  --effort high \
  --context-tokens 80000 \
  --input-tokens 60000 \
  --output-tokens 8000
```

The returned JSON includes the selected model and estimated token cost. The custom agent uses that result to recommend a model; VS Code must still start a new chat with that model because an already-running agent cannot change its own model.