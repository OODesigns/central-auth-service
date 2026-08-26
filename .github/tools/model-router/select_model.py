#!/usr/bin/env python3
"""Select the lowest-cost available model that meets explicit task requirements."""

import argparse
import json
from pathlib import Path


EFFORT_RANK = {"low": 1, "medium": 2, "high": 3}


def estimated_cost(model: dict, input_tokens: int, output_tokens: int) -> float:
    return (
        model["input_per_million"] * input_tokens
        + model["output_per_million"] * output_tokens
    ) / 1_000_000


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task", required=True, help="Required capability from the model catalog.")
    parser.add_argument("--effort", choices=EFFORT_RANK, required=True)
    parser.add_argument("--context-tokens", type=int, required=True)
    parser.add_argument("--input-tokens", type=int, default=20_000)
    parser.add_argument("--output-tokens", type=int, default=4_000)
    parser.add_argument(
        "--catalog",
        type=Path,
        default=Path(__file__).with_name("models.json"),
        help="Path to a current model and pricing catalog.",
    )
    arguments = parser.parse_args()

    with arguments.catalog.open(encoding="ascii") as catalog_file:
        catalog = json.load(catalog_file)

    candidates = [
        model
        for model in catalog["models"]
        if model["available"]
        and arguments.task in model["capabilities"]
        and EFFORT_RANK[model["max_effort"]] >= EFFORT_RANK[arguments.effort]
        and model["max_context_tokens"] >= arguments.context_tokens
    ]
    if not candidates:
        raise SystemExit(
            "No model meets the requested capability, effort, and context requirements. "
            "Update models.json with an available compatible model."
        )

    selected = min(
        candidates,
        key=lambda model: estimated_cost(
            model, arguments.input_tokens, arguments.output_tokens
        ),
    )
    result = {
        "model": selected["name"],
        "task": arguments.task,
        "effort": arguments.effort,
        "context_tokens": arguments.context_tokens,
        "estimated_cost_usd": round(
            estimated_cost(selected, arguments.input_tokens, arguments.output_tokens), 6
        ),
        "rates_unit": catalog["rates_unit"],
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()