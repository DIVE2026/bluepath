#!/usr/bin/env python3
"""Compare a baseline endpoint and BluePath candidate on identical held-out cases."""
from __future__ import annotations

import json
import os
from pathlib import Path

from finetuning.evaluate_model import evaluate

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    baseline = evaluate(
        os.environ["BLUEPATH_BASELINE_URL"],
        os.environ["BLUEPATH_BASELINE_MODEL"],
        os.getenv("BLUEPATH_BASELINE_API_KEY", ""),
    )
    candidate = evaluate(
        os.environ["BLUEPATH_EVAL_BASE_URL"],
        os.environ["BLUEPATH_EVAL_MODEL"],
        os.getenv("BLUEPATH_EVAL_API_KEY", ""),
    )

    category_names = sorted(set(baseline["categories"]) | set(candidate["categories"]))
    category_deltas = {}
    regressed_categories = []
    tolerance = float(os.getenv("BLUEPATH_CATEGORY_REGRESSION_TOLERANCE", "0.0"))
    for category in category_names:
        baseline_rate = baseline["categories"].get(category, {}).get("passRate", 0.0)
        candidate_rate = candidate["categories"].get(category, {}).get("passRate", 0.0)
        delta = candidate_rate - baseline_rate
        category_deltas[category] = {
            "baselinePassRate": baseline_rate,
            "candidatePassRate": candidate_rate,
            "delta": delta,
        }
        if delta < -tolerance:
            regressed_categories.append(category)

    minimum_rate = float(os.getenv("BLUEPATH_MIN_EVAL_RATE", "0.85"))
    minimum_delta = float(os.getenv("BLUEPATH_MIN_PASS_RATE_DELTA", "0.0"))
    max_regressed_categories = int(os.getenv("BLUEPATH_MAX_REGRESSED_CATEGORIES", "0"))
    pass_rate_delta = candidate["passRate"] - baseline["passRate"]

    report = {
        "baseline": baseline,
        "candidate": candidate,
        "passRateDelta": pass_rate_delta,
        "categoryDeltas": category_deltas,
        "regressedCategories": regressed_categories,
        "gates": {
            "candidateMeetsMinimum": candidate["passRate"] >= minimum_rate,
            "candidateMeetsMinimumDelta": pass_rate_delta >= minimum_delta,
            "categoryRegressionWithinLimit": len(regressed_categories) <= max_regressed_categories,
            "candidateRequestsSucceeded": candidate["requestErrors"] == 0,
            "baselineRequestsSucceeded": baseline["requestErrors"] == 0,
        },
    }
    report["passed"] = all(report["gates"].values())

    output = Path(
        os.getenv(
            "BLUEPATH_COMPARE_OUTPUT",
            str(ROOT / "finetuning/output/model_comparison.json"),
        )
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "baselinePassRate": baseline["passRate"],
                "candidatePassRate": candidate["passRate"],
                "delta": pass_rate_delta,
                "regressedCategories": regressed_categories,
                "passed": report["passed"],
                "report": str(output),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    raise SystemExit(0 if report["passed"] else 1)


if __name__ == "__main__":
    main()
