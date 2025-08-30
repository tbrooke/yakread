# Yakread

Source code for [Yakread](https://yakread.com), a reading app that does:

- RSS and newsletter subscriptions
- bookmark URLs to read them later
- algorithmic recommendation, both for helping you manage all the stuff you subscribe to/bookmark
  and for showing you articles that other Yakread users like.

I made Yakread during the final stretch of my "entrepreneurship journey;" now I have a regular job
and Yakread is a side project. I've open sourced it primarily so it can serve as an example of a
nontrivial project built with [Biff](https://biffweb.com/), a Clojure web framework I built and
maintain. You're welcome to self-host it if you want, but I haven't written any documentation for
doing so (other than [Biff's standard deployment
instructions](https://biffweb.com/docs/reference/production/)).

Yakread is also a testing ground for new Biff features; expect to see a bunch of rough unpolished
stuff that may or may not get moved into the framework later.

## Running the app locally

1. Run `clj -M:run generate-config`
2. Create 4 S3 buckets (I use DigitalOcean Spaces) and set the `S3_*` env vars in config.env
3. Run `clj -M:run dev`

Then go to `localhost:8080`.

## Code structure

First, read [Project Layout](https://biffweb.com/docs/reference/project-layout/) and
[Architecture](https://biffweb.com/docs/reference/architecture/) from the Biff docs.

TODO

## Blog posts

- [Structuring large Clojure codebases with Biff](https://biffweb.com/p/structuring-large-codebases/)
- [You can help unbundle social media](https://obryant.dev/p/you-can-unbundle-social-media/)

---

[Jacob O'Bryant](https://obryant.dev)
