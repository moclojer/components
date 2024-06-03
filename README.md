# Components

These are all the components we use at moclojer. Specifically:

- config.clj
- database.clj
- db_utils.clj
- http.clj
- logs.clj
- migrations.clj
- redis_publisher.clj
- redis_queue.clj
- router.clj
- sentry.clj
- storage.clj
- webserver.clj

We use stuartsierra's [component](https://github.com/stuartsierra/component) library for state management.

## How to use

This lib is supposed to be plug & play.

## With Clojars

Add this to your `deps.edn`:

```clj
{:deps
 {com.moclojer/components {:mvn/version "0.0.3"}}}

```


## Github

Add this to your `deps.edn`:

```clj
{:deps
 {com.moclojer/components {:git/url "https://github.com/moclojer/components.git"
                           :git/sha "2bdde1fc65458b644c6698891c09852bb5b10caa"}}}
```
