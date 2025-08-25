# Yakread

Source code for [Yakread](https://yakread.com), a reading app that does:

- RSS and newsletter subscriptions
- bookmark URLs to read them later
- algorithmic recommendation, both for helping you manage all the stuff you subscribe to/bookmark
  and for showing you articles that other Yakread users like.

I made Yakread during the final stretch of my "entrepreneurship journey;" now I have a regular job
and Yakread is a side project. I've open sourced it primariy so it can serve as an example of a
nontrivial project built with [Biff](https://biffweb.com/), a Clojure web framework I built and
maintain. You're welcome to self-host it if you want, but I haven't written any documentation for
doing so (other than [Biff's standard deployment
instructions](https://biffweb.com/docs/reference/production/)).

Yakread is also a testing ground for new Biff features; expect to see a bunch of rough unpolished
stuff that may or may not get moved into the framework later.

To run the app locally, do `clj -M:dev` and then go to `localhost:8080`.

You might enjoy this blog post: [You can help unbundle social media](https://obryant.dev/p/you-can-unbundle-social-media/).

[Jacob O'Bryant](https://obryant.dev)
