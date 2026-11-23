Feature: The form runs in a WebView that only the form's own origin can talk to
  As a publisher's tech team
  I want the Lead Generation form view locked to the form's origin
  So that no other page, frame or file can reach my app through it or read what the viewer types

  Proved on a real WebView (instrumented suite). Pages are served over HTTPS from the test APK.

  Scenario: the form page at the launch origin reaches the app
    Given a form view for a launch URL
    When the page at that origin posts a bridge message
    Then the app receives it

  Scenario: the form receives the app's messages
    Given a form view whose page has posted a message
    When the app sends a message
    Then the page receives exactly that text

  Scenario: the page learns its instance and channel from the launch URL fragment
    Given a form view for a launch URL
    When the page loads
    Then its address carries the instance and the channel in the fragment only

  Scenario: a frame from another origin cannot reach the app
    Given a form page that embeds a frame from another origin
    When the frame tries to post a bridge message
    Then the app receives nothing

  Scenario: a frame from the same origin cannot reach the app
    Given a form page that embeds a frame from its own origin
    When the frame tries to post a bridge message
    Then the app receives nothing

  Scenario: the form cannot navigate away
    Given a loaded form view
    When the page tries to navigate to another address
    Then the view stays on the form
    And the form fails and the app is told once

  Scenario: the form cannot submit a page form or load a second page
    Given a loaded form view
    When the page submits a form with POST or loads a second page
    Then the request is refused
    And the form fails and the app is told
    But the images, scripts and other resources of the form still load

  Scenario: a form page that answers an error is reported to the app
    Given a form view
    When the form's page answers with an HTTP error such as 404
    Then the app is told once, with the status

  Scenario: the form cannot open windows or popups
    Given a loaded form view
    When the page tries to open a new window
    Then no window opens

  Scenario: files and content providers are out of reach
    Given a loaded form view
    When the page tries to load a file or content address
    Then the load is refused

  Scenario: the form view is locked down
    Given a form view
    Then file access, content access, mixed content, multiple windows and DOM storage are off

  Scenario: destroying the view removes the bridge
    Given a form view whose page has posted a message
    When the view is destroyed
    Then later posts from the page reach nothing
    And the view is detached from its parent
