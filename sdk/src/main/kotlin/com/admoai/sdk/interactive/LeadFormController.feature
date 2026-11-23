Feature: An app prepares and opens a Lead Generation form through one controller
  As a publisher's tech team
  I want one controller per served Lead Generation creative that prepares the form in the background and opens it on tap
  So that the viewer gets the form quickly, once, inline or full-screen as my app chose

  The app still renders the creative and fires its impression and its one click. The controller
  never fires a click, never fetches another ad and never shows a stale form.

  Background:
    Given an app that opted in to Lead Generation
    And a served creative with a usable Lead Generation descriptor

  Scenario: an app that has not opted in gets no controller
    Given an app that has not opted in
    When it asks for a controller for the creative
    Then it gets none

  Scenario: a creative without a usable descriptor gets no controller
    When the app asks for a controller for a creative whose experience is unsupported
    Then it gets none
    And a creative whose form instance the bridge cannot carry gets none either

  Scenario: preparing loads the form hidden and stops before activation
    When the app prepares the controller
    And the form reports it is ready
    Then the controller is prepared
    And the form was told the app's presentation and the preparation token
    And nothing was activated

  Scenario: opening a prepared form activates it, prefills and makes it ready to show
    Given a prepared controller
    When the app opens it
    Then the form is activated
    And it receives a prefill
    And once the prefill is applied the controller is ready to show

  Scenario: a form becomes visible only when the app says it is on screen
    Given a controller ready to show
    When the app reports the form visible
    Then the controller is visible
    And the form was told it became visible once

  Scenario: tapping before preparation finished opens the form once it is ready
    Given a controller still preparing
    When the app opens it
    And the form reports it is ready
    Then the form is activated without a second load

  Scenario: preparing or opening twice never creates a second form
    When the app prepares the controller twice and opens it twice
    Then one form view was loaded
    And the form was activated once

  Scenario: hiding and showing again keeps the same form
    Given a visible controller
    When the app reports it hidden and then visible
    Then the same form view is kept
    And the form was told it became hidden and visible again

  Scenario: a preparation that expired never opens
    Given a controller whose preparation expired
    When the app opens it
    Then the controller is expired
    And nothing was sent to the form

  Scenario: a retryable preparation failure can be retried in the same view
    Given a controller whose preparation failed with a retryable error
    When the app retries
    Then the form is asked to prepare again in the same view

  Scenario: a retryable activation failure retries activation
    Given a controller whose activation failed with a retryable error
    When the app retries
    Then the form is asked to activate again

  Scenario: a failure that cannot be retried ends the controller
    When the form reports a fatal error
    Then the controller has failed without retry
    When the form reports a preparation failure the contract marks final, even if the page calls it retryable
    Then the controller has failed without retry

  Scenario: the form reports a submission in progress and its outcome
    Given a visible controller
    When the form reports it is submitting and then submitted
    Then the controller goes through submitting to submitted with the lead id

  Scenario: a failed submission keeps the same form for a retry
    Given a visible controller that is submitting
    When the form reports the submission failed and can be retried
    Then the controller is visible again with the same form view

  Scenario: the form asks to close
    Given a visible controller
    When the form asks to close
    Then the app's close callback is called with the controller

  Scenario: the privacy policy link goes to the app, never inside the form
    Given a visible controller
    When the form asks to open its privacy policy
    Then the app's link callback gets the privacy policy purpose and its URL

  Scenario: the website link is refused before a submission
    Given a visible controller
    When the form asks to open the website before submitting
    Then the app's link callback is not called

  Scenario: the website link goes to the app after a submission
    Given a submitted controller
    When the form asks to open the website
    Then the app's link callback gets the website purpose and its URL

  Scenario: a page that reloads after activation fails the controller
    Given a visible controller
    When the form reports it is ready again
    Then the controller has failed without retry

  Scenario: a form that tries to leave its page fails
    Given a visible controller
    When the form view refuses a navigation the form tried
    Then the controller has failed without retry
    And the view is released

  Scenario: a form page that is not available fails and cannot be retried
    Given a controller that is preparing or was opened by a tap
    When the form's page is not available (the server answers 404, or any other 4xx except 408 and 429)
    Then the controller has failed with form_not_available and cannot be retried
    And the view is released

  Scenario: a form page that fails for now can be loaded again in the same view
    Given a controller that is preparing or was opened by a tap
    When the form's page answers a server error (5xx), rate limiting (429) or a timeout (408), or cannot be reached
    Then the controller has failed with temporary_failure and can be retried
    And a retry loads the form's page again in the same view

  Scenario: a page failure reported after the form's bridge is up does not end the form
    Given a prepared controller
    When a page load failure is reported
    Then the controller stays prepared

  Scenario: a page failure reported after the app disposed the controller changes nothing
    Given a disposed controller
    When a page load failure is reported
    Then the controller stays disposed

  Scenario: a tap that was queued while preparing opens the form once, even if the app opens it at the same moment
    Given a controller whose tap was queued while preparing
    When the form reports it is prepared and the app opens it right then
    Then the form is activated once

  Scenario: a tap queued before the preparation expired does not activate it afterwards
    Given a controller whose tap was queued while preparing
    When the form reports it is prepared after the preparation expired
    Then the controller is expired
    And the form was never asked to activate

  Scenario: retrying an activation after the preparation expired does not activate it
    Given a controller whose activation failed with a retryable error
    When the preparation expires and the app retries
    Then the controller is expired
    And the form is not asked to activate again

  Scenario: a prefill that cannot be sent fails the form instead of leaving it opening forever
    Given a controller that was opened and activated
    When the prefill cannot be delivered to the form
    Then the controller has failed with bridge_error and cannot be retried

  Scenario: a form shown at the moment it is ready is told it is visible once
    Given a controller whose app shows the form as soon as it is ready to show
    When the form reports its prefill was applied
    Then the form was told it became visible once

  Scenario: disposing ends the form and ignores everything after
    Given a visible controller
    When the app disposes it
    And the form sends any message afterwards
    Then the form was told to dispose
    And the controller stays disposed
    And no app callback is called

  Scenario: the controller's description never shows the preparation token
    Given a prepared controller
    When the app prints the controller or its state
    Then the preparation token does not appear
