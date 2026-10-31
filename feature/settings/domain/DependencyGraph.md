# `:feature:settings:domain`

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :feature
    direction TB
    subgraph :feature:settings
      direction TB
      :feature:settings:domain[domain]:::domain
    end
  end
  subgraph :core
    direction TB
    :core:domain[domain]:::domain
  end

  :feature:settings:domain -.-> :core:domain

classDef app fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef domain fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
classDef core fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
```

<!--endregion-->
