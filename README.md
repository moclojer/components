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
 {com.moclojer/moclojer {:git/url "https://github.com/moclojer/moclojer.git"
                         :git/sha "04aaa7480d04dc4043ea0f8cdf3108e5575d4a10"}}}
```
