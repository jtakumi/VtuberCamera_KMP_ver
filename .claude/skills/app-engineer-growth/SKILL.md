---
name: app-engineer-growth
description: Analyze an app developer's skills and growth from an application repository, especially jtakumi/VtuberCamera_KMP_ver. Use Git history, source code, tests, pull requests, and review discussions to map demonstrated engineering skills, compare them with a career-stage framework, identify evidence-backed gaps, recommend current training resources, and cautiously describe work patterns visible in implementation and conversation records.
argument-hint: 'Repository/ref, experience range, and areas to assess'

---

# App Engineer Growth

Assess demonstrated engineering capability from repository evidence and produce a practical development plan. Treat the repository as evidence of work, not a complete measure of the engineer.

## Workflow

1. **Establish scope and stage.** Use `jtakumi/VtuberCamera_KMP_ver` when the user says “my app repository” without specifying another repository. Confirm or infer the experience range from conversation or user-provided background; label the source and date. If years of experience are unknown, ask briefly or show a provisional comparison across relevant bands instead of asserting a specific tenure.
2. **Collect evidence.** Read the repository overview and relevant source files. Inspect Git history (commit metadata and diffs), merged and open PRs, review threads, tests, CI/workflows, and issues where accessible. Use the GitHub connector or local Git history; for a fuller history, clone/fetch the repo if the environment permits. Sample across time and areas instead of over-weighting the latest or largest commits. Include authored work and substantial AI-assisted work only when the user’s contribution is evidenced; do not infer authorship from commit identity alone.
3. **Build a skill map.** Rate each area as `demonstrated`, `emerging`, `insufficient evidence`, or `not observed`. Tie every rating to examples and dates. Consider app architecture, Kotlin/Compose, platform APIs, async/state/data, testing, debugging, build/CI, performance/reliability, security/privacy, accessibility/UX, code review, delivery, and technical communication. Tailor the categories to the actual stack and repository.
4. **Compare with career stage.** Use the framework below as a discussion aid, not a universal grading rubric. Compare expectations to observed evidence, account for role and opportunity, and distinguish skill gaps from missing chances to demonstrate a skill.
5. **Prioritize practice.** Choose at most three high-value gaps. For each, recommend a concrete training exercise and, when useful, a current course/site/book. Browse for resources because availability and editions change; verify links, level, cost/access, and relevance. Explain why each resource fits the observed gap.
6. **Describe work tendencies cautiously.** If conversation records are available, analyze only repeated, directly evidenced work preferences or approaches. Separate observed behavior from tentative interpretation; avoid diagnosing personality, mental health, aptitude, or fixed traits. Give counterexamples and confidence. Omit this section when evidence is too thin.

## Evidence and scoring

- Prefer changes that show design decisions, trade-offs, iteration, tests, and handling of failures over raw commit counts, lines changed, language counts, or repository size.
- A commit may be a merge, dependency bot update, generated change, formatting-only edit, or pair/AI-assisted work. Classify these before attributing capability.
- Distinguish “not seen in this repository” from “cannot do.” A single repository rarely reveals product discovery, collaboration, operations, or the engineer’s full portfolio.
- Use calibrated levels only when useful: `初学相当 / 自立して実装 / 設計・改善を主導 / 横断的に牽引`. Explain what evidence supports each; do not turn the levels into a precise score.
- If GitHub or local history is unavailable, state the access limit and base the analysis only on inspected material. Never fabricate commits, dates, review feedback, experience duration, or resource details.

## Career-stage comparison guide

Adapt expectations to the engineer’s actual responsibilities and opportunity. Treat bands as approximate:

| Experience | Typical growth focus (not a pass/fail bar) |
| --- | --- |
| 0–2 years | Deliver scoped changes with guidance; understand code paths; write basic tests; use logs/debuggers; learn platform and team practices. |
| 2–5 years | Own features end to end; make local design trade-offs; investigate production issues; maintain tests and reliability; communicate risks and coordinate changes. |
| 5–8 years | Lead ambiguous work; improve architecture and delivery practices; mentor; align technical choices across modules or teams; measure outcomes. |
| 8+ years | Shape technical direction and organizational capability; handle multi-team/system trade-offs; grow others; connect engineering choices to product and business outcomes. |

Do not equate seniority with title, speed, commit volume, or management. Adjust for part-time exposure, team structure, project constraints, and time spent in each technical area.

## Output format

1. **対象と前提** — repository/ref, evidence period, known experience stage, access limitations.
2. **要約** — 2–4 evidence-based strengths and one caveat.
3. **スキルマップ** — area, observed level, evidence (path/PR/commit and date), and confidence.
4. **経験段階との比較** — where evidence aligns, where it is ahead/behind/unknown, and opportunity constraints.
5. **優先課題と練習案** — up to three actionable exercises with success criteria; include checked resources and links.
6. **仕事上の傾向（任意）** — observed behavior, possible interpretation, confidence, and alternative explanation.
7. **次の30日** — a small sequenced plan.

Keep judgments constructive and specific. Cite GitHub permalinks or file paths and date evidence inline. Do not use the language of psychological diagnosis or claim that repo activity alone reveals personality.

## Independent final review

Before presenting a completed assessment, use a subagent for an independent review whenever subagents are available. A lower-cost or older model is acceptable.

- Give the reviewer the inspected source files, Git diffs, tests, cited sources, and assessment criteria. Do not disclose your conclusions or the answer you expect; ask them to evaluate the evidence independently.
- Ask them to check claim-to-evidence fit, authorship and AI-assistance attribution, career-stage comparisons, counterevidence and uncertainty, current resource links and fit, and overreach in any work-tendency observations.
- Verify the reviewer's points against primary evidence. Incorporate valid corrections, reject unsupported suggestions, and never treat the reviewer's opinion as fact.
- Briefly report that the subagent review occurred, material corrections made, and remaining uncertainty.
- If no subagent can be started, state that independent review was not performed and do not describe the assessment as subagent-verified. Do a separate evidence checklist if practical and disclose its limits.
