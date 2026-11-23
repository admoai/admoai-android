Feature: Lead Generation forms are bounded and never outlive the user they were opened for
  As an ad-viewer
  I want a form opened for me to close when someone else signs in to the app
  So that my details never appear in another person's form, and hidden forms never weigh on the app

  The bound on hidden prepared forms comes from the shared contract (maxHiddenPreparedForms).

  Scenario: a user change closes every open form
    Given two forms prepared for user A, one of them visible
    When the app sets a different user
    Then both forms are disposed

  Scenario: a lookup that answers after the user changed fills nothing
    Given a form waiting for user A's values
    When the app signs user A out and the lookup then answers
    Then the form is disposed
    And no prefill was sent

  Scenario: a lookup answer that arrives after the user changed but before the forms are disposed sends nothing
    Given a form waiting for user A's values and the closing of forms still waiting to run
    When the app signs user A out and the lookup answers before the forms are closed
    Then no prefill was sent
    And the form is disposed

  Scenario: a form created while the app opts out is not kept
    Given a form being created
    When the app turns the opt-in off before the form is registered
    Then the app gets no form

  Scenario: a form created after a user change is not closed by the earlier change
    Given a form prepared for user A and the closing of forms still waiting to run
    When the app sets user B and a new form is prepared for user B
    And the waiting closing runs
    Then the form of user A is disposed
    And the form of user B is still prepared

  Scenario: setting the same user again keeps the forms
    Given a form prepared for user A
    When the app sets user A again
    Then the form is still prepared

  Scenario: turning the opt-in off or changing the value provider closes every form
    Given a visible form
    When the app turns the opt-in off, or opts in again with another value provider
    Then the form is disposed

  Scenario: the app can close every form at once
    Given two prepared forms
    When the app asks the SDK to dispose all forms
    Then both forms are disposed

  Scenario: hidden prepared forms are bounded
    Given as many hidden prepared forms as the contract allows
    When the app prepares one more
    Then that form does not load yet
    And when the viewer opens it, it loads and opens normally

  Scenario: a form that becomes visible no longer counts toward the bound
    Given as many hidden prepared forms as the contract allows
    When one of them becomes visible
    Then another form can prepare

  Scenario: a disposed form frees its place
    Given as many hidden prepared forms as the contract allows
    When one of them is disposed
    Then another form can prepare
