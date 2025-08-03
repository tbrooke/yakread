(ns com.yakread.routes)

(def signin-page       'com.yakread.app.home/signin-page-route)
(def contact           'com.yakread.app.home/contact-route)
(def about             'com.yakread.app.home/about-route)
(def tos               'com.yakread.app.home/tos-route)
(def privacy           'com.yakread.app.home/privacy-route)

(def subs-page         'com.yakread.app.subscriptions/page-route)
(def unsubscribe!      'com.yakread.app.subscriptions/unsubscribe)
(def add-sub-page      'com.yakread.app.subscriptions.add/page-route)
(def view-sub-page     'com.yakread.app.subscriptions.view/page-route)

(def favorites-page    'com.yakread.app.favorites/page)
(def add-favorite-page 'com.yakread.app.favorites.add/page)

(def bookmarks-page    'com.yakread.app.read-later/page)
(def add-bookmark-page 'com.yakread.app.read-later.add/page)

(def read-item         'com.yakread.app.for-you/read-page-route)
(def for-you           'com.yakread.app.for-you/page-route)
(def history           'com.yakread.app.for-you.history/page)
(def click-ad          'com.yakread.app.for-you/click-ad-route)
(def click-item        'com.yakread.app.for-you/click-item-route)

(def settings-page     'com.yakread.app.settings/page)
(def unsubscribe       'com.yakread.app.settings/click-unsubscribe-route)
(def set-timezone      'com.yakread.app.settings/set-timezone)
(def stripe-webhook    'com.yakread.app.settings/stripe-webhook)
