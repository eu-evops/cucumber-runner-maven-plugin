Feature: One

  Scenario Outline: Scenario 1
    Given I kill thread if <value> is more than 20

    Examples:
      | value |
      | 10    |
      | 15    |
      | 22    |
      | 30    |

