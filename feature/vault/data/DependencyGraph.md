# `:feature:vault:data`

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
    subgraph :feature:vault
      direction TB
      :feature:vault:data[data]:::core
      :feature:vault:domain[domain]:::domain
    end
  end
  subgraph :core
    direction TB
    :core:common[common]:::core
    :core:data[data]:::core
    :core:domain[domain]:::domain
  end

  :core:data -.-> :core:common
  :core:data -.-> :core:domain
  :feature:vault:data -.-> :core:data
  :feature:vault:data -.-> :core:domain
  :feature:vault:data -.-> :feature:vault:domain
  :feature:vault:domain -.-> :core:domain

classDef app fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef domain fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
classDef core fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
```

<!--endregion-->
