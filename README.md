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

## With Clojars (TODO)

## Github

Add this to your `deps.edn`:

```clj
{:deps
 {com.moclojer/components {:git/url "https://github.com/moclojer/components.git"
                           :git/sha "d8e9935a8fd84575167774298e37145b0c933f73"}}}
```
