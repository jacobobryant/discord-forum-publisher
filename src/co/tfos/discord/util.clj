(ns co.tfos.discord.util)

(defn email-signin-enabled? [sys]
  (every? sys [:mailersend/api-key :recaptcha/site-key :recaptcha/secret-key]))
