Feature: The SDK's log never shows a Lead Generation credential or a tracking query value
  As an ad-viewer
  I want the opaque tokens and tracking values that identify my form to stay out of the app's log
  So that a log file, a bug report or a crash trace never carries anything that could be replayed

  Turning the SDK's logging on prints the requests and responses it sends. Every query value of
  every URL, and the form's three tokens, are replaced by REDACTED; the address and the query keys stay.

  Scenario: a form event's tracking query never appears in the SDK's log
    Given an app with SDK logging on
    When a form event's tracking URL is fired
    Then the logged request shows the tracking address without its query values

  Scenario: a form event on any path has its query values redacted in the SDK's log
    Given an app with SDK logging on
    When a form event's URL on a path that is not the tracking path is logged
    Then the logged line shows its address and query keys without the query values

  Scenario: query values the engine wrote with an escaped ampersand are redacted too
    Given an app with SDK logging on
    When a response body is logged with a URL whose parameters are separated by the JSON escape of an ampersand
    Then every query value of that URL is redacted
    And the separators stay as written

  Scenario: the form's tokens are redacted from logged JSON
    Given an app with SDK logging on
    When a body with a preparation token is logged
    Then the token is replaced and the rest of the body is untouched
