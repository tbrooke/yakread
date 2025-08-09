# Yakread

I am in the middle of migrating the code for [Yakread](https://yakread.com) from a different private repo into this
open-source repo. I'm rewriting the codebase as I do it, so the process is slow. The code is very messy, which is why
I'm rewriting it. During this migration I'll be copying over a lot of code too though, so be warned.

I'm using Yakread as a playground for new [Biff](https://biffweb.com) features: I'll periodically move code from here to
there. See [Experimental Biff features for growing Clojure projects](https://biffweb.com/p/experimental-features/).

Run the app with `clj -M:run dev`. Go to `http://localhost:8080/subscriptions/add` to sign in. Afterward you'll be
greeted with a page that says "TODO." The console output will have a sign-in link; click on that.

## Progress

Done:

- Subscribe to RSS feeds
- View a list of your subscriptions
- Pin and unpin subscriptions
- View a list of posts from a particular subscription

In-progress:
- Subscribe to newsletters (you can make an email address, but it won't actually receive emails. Also need to make the
  domain configurable.)
- Read a post from the subscriptions tab

To do:
- everything else

I won't be porting over Yakread's add system. Instead I'll change the premium subscription to a pay-what-you-want model. I'm
actually one of three people in the tech industry who think that ads are (or rather, can be) good; however, for [digital
public infrastructure](https://publicinfrastructure.org/) I think pay-what-you-want is a better model.
