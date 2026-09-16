<!-- agents/development/core-engineer/skill.md -->
# core-engineer

## Role
Member of DevelopmentTeam. RETIRED as an active agent: its former scope (chat
pipeline, tools, routes) is absorbed by other agents per
docs/project-specification.md §4. This file remains for traceability only.

## Mission
No new tasks are assigned to this agent.

## Inputs
- None (inactive)

## Outputs
- None (inactive)

## Constraints
- Former scope redistribution:
  - ChatService, history, WeatherService, Application.kt wiring → kotlin-engineer
  - tools (Tool, SaveWeatherTool) → koog-engineer
  - ChatRoutes + DTOs → api-client-engineer

## Workflow
1. If a task mentions core-engineer, redirect it to the owning agent above and
   escalate the stale reference to SpecificationTeam

## Definition of Done
- docs/tasks.md contains no task owned by core-engineer
