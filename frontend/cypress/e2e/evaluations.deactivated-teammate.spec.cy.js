// UC-EVA-submit-evaluation step 2, after UC-STU-deactivate-student: the submit form lists only active teammates.
// The team roster and the student's own evaluations for the week are stubbed; the course section and rubric
// come from the dev seed. EvaluationIntegrationTest covers the server refusing a rating of her (9a).
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const teammate = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName.toLowerCase()}@abc.edu`,
  teamName: 'Team1',
  enabled
})

describe('Submit peer evaluations with a deactivated teammate', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', '**/students/teams/*', {
      body: {
        flag: true,
        code: 200,
        message: 'Find students by team id successfully',
        data: [
          teammate(4, 'John', 'Smith', true),
          teammate(5, 'Eric', 'Hudson', false),
          teammate(6, 'Jerry', 'Moon', true)
        ]
      }
    }).as('team')
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', {
      body: { flag: true, code: 200, message: 'Find evaluations successfully', data: [] }
    }).as('mine')
  })

  it('does not ask the student to evaluate a deactivated teammate (step 2)', () => {
    cy.visit('/evaluations/submit-evaluations')
    cy.wait('@team')
    cy.wait('@mine')

    cy.get('.el-table__body').should('contain', 'John Smith').and('contain', 'Jerry Moon')
    cy.get('.el-table__body').should('not.contain', 'Eric Hudson')
  })
})
