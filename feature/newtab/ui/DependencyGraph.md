# `:feature:newtab:ui`

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
    subgraph :feature:newtab
      direction TB
      :feature:newtab:ui[ui]:::core
    end
    subgraph :feature:terminal
      direction TB
      :feature:terminal:domain[domain]:::domain
    end
  end
  subgraph :core
    direction TB
    :core:domain[domain]:::domain
    :core:tabs[tabs]:::core
    :core:ui[ui]:::core
  end

  :core:tabs -.-> :core:domain
  :core:ui -.-> :core:domain
  :core:ui -.-> :core:tabs
  :feature:newtab:ui -.-> :core:domain
  :feature:newtab:ui -.-> :core:tabs
  :feature:newtab:ui -.-> :core:ui
  :feature:newtab:ui -.-> :feature:terminal:domain
  :feature:terminal:domain -.-> :core:domain

classDef app fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef domain fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
classDef core fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
```

<!--endregion-->
