// UC-STU-deactivate-student: the Deactivate button and its confirmation on the Edit student form. The student API
// is stubbed, so these tests check what the form offers, what it sends, and what it shows; the backend's own tests
// (StudentDeactivationIntegrationTest) cover who may deactivate and what deactivation keeps.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const student = (enabled) => ({
  id: 77,
  username: 'w.allen@abc.edu',
  firstName: 'Woody',
  lastName: 'Allen',
  email: 'w.allen@abc.edu',
  teamName: 'Team2',
  enabled
})

const page = (enabled) => ({
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: {
    content: [student(enabled)],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10
  }
})

describe('Deactivate a student', () => {
  let enabled

  beforeEach(() => {
    enabled = true
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', (req) => req.reply({ body: page(enabled) })).as(
      'search'
    )
    cy.intercept('GET', '**/students/77', (req) =>
      req.reply({
        body: {
          flag: true,
          code: 200,
          message: 'Find student successfully',
          data: student(enabled)
        }
      })
    ).as('student')
    cy.intercept('PATCH', '**/students/77/deactivate', (req) => {
      enabled = false
      req.reply({
        body: {
          flag: true,
          code: 200,
          message: 'Deactivate student successfully',
          data: student(false)
        }
      })
    }).as('deactivate')
  })

  function openEditDialog() {
    cy.visit('/students')
    cy.wait('@search')
    cy.get('.el-table__row').first().find('button').click()
    cy.wait('@student')
  }

  it('offers no Deactivate button for an already-deactivated student (3a)', () => {
    enabled = false
    openEditDialog()

    cy.get('[data-cy=student-status-switch]').should('exist')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })

  it('sends nothing when the user cancels at the warning (5a)', () => {
    openEditDialog()

    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').should('be.visible').contains('button', 'Cancel').click()

    cy.get('@deactivate.all').should('have.length', 0)
    cy.get('[data-cy=deactivate-student]').should('exist') // Still active, still offered
  })

  it('shows the consequences, deactivates on confirm, and the list shows Disabled (steps 3 to 6)', () => {
    openEditDialog()
    cy.get('[data-cy=student-status-switch]').should('have.class', 'is-disabled') // Only the button deactivates

    cy.get('[data-cy=deactivate-student]').click()
    cy.get('[data-cy=deactivate-consequences]')
      .should('contain', 'no longer be able to log in')
      .and('contain', 'team membership are kept')
      .and('contain', 'can be reactivated later')
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@deactivate')
    cy.get('.el-message--success').should('contain', 'Woody Allen has been deactivated.')
    cy.wait('@search')
    cy.get('.el-table__row').first().should('contain', 'Disabled')
  })

  it('reports a student deactivated since the dialog opened and reloads the list (6a)', () => {
    cy.intercept('PATCH', '**/students/77/deactivate', (req) => {
      enabled = false
      req.reply({
        statusCode: 409,
        body: { flag: false, code: 409, message: 'This student is already deactivated.' }
      })
    }).as('conflict')
    openEditDialog()

    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@conflict')
    cy.get('.el-message--error').should('contain', 'This student is already deactivated.')
    cy.wait('@search')
    cy.get('.el-table__row').first().should('contain', 'Disabled')
  })
})
