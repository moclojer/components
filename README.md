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

## Clojars

Add this to your `deps.edn`:

```clj
{:deps
 {com.moclojer/components {:mvn/version "0.x.x"}}}

```

## Github

Add this to your `deps.edn`:

```clj
{:deps
 {com.moclojer/components {:git/url "https://github.com/moclojer/components.git"
                           :git/sha "<commit sha>"}}}
```
