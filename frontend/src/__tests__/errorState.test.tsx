/**
 * A refused request is not a broken application, and the UI must not say it is.
 *
 * Regression context: fifteen pages rendered `<ErrorState retry={...} />` with no
 * message, so ErrorState fell back to its defaults — "Something went wrong /
 * Failed to load data. Please try again." That string appeared for every
 * failure, so a Cashier opening a page their role legitimately cannot use was
 * told the app was broken and offered a "Try Again" button that could never
 * succeed. A real 500 looked identical, which is the worse half: it taught
 * everyone to ignore the error screen.
 *
 * The fix is to hand the error to ErrorState and let it tell a refusal apart from
 * a fault.
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ErrorState from '@/components/ui/ErrorState';

describe('ErrorState — telling a refusal from a fault', () => {
  it('says the user lacks access when the server refused', () => {
    render(<ErrorState error="You don't have permission to do that." retry={() => {}} />);

    expect(screen.getByText('You do not have access to this')).toBeInTheDocument();
    expect(
      screen.getByText("You don't have permission to do that."),
    ).toBeInTheDocument();
  });

  it('does not offer to retry a refusal', () => {
    const retry = jest.fn();
    render(<ErrorState error="Access denied. You do not have permission." retry={retry} />);

    expect(screen.queryByRole('button', { name: /try again/i })).not.toBeInTheDocument();
  });

  it('still says something went wrong for a genuine failure', () => {
    render(<ErrorState error="Something broke on the server" retry={() => {}} />);

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('offers to retry a failure, because a retry might work', async () => {
    const retry = jest.fn();
    const user = userEvent.setup();
    render(<ErrorState error="Failed to load data" retry={retry} />);

    await user.click(screen.getByRole('button', { name: /try again/i }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('accepts an Error, not just a string', () => {
    render(<ErrorState error={new Error('Network Error')} />);
    expect(screen.getByText('Network Error')).toBeInTheDocument();
  });

  it('recognises the several ways a refusal is worded', () => {
    const phrasings = [
      'Missing authority INVENTORY_READ',
      'You do not have permission.',
      'Forbidden',
      'not authorised',
    ];
    for (const phrasing of phrasings) {
      const { unmount } = render(<ErrorState error={phrasing} />);
      expect(screen.getByText('You do not have access to this')).toBeInTheDocument();
      unmount();
    }
  });

  it('falls back to its defaults when given nothing', () => {
    render(<ErrorState />);
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Failed to load data. Please try again.')).toBeInTheDocument();
  });

  it('lets an explicit title override the inferred one', () => {
    render(<ErrorState title="Stock could not be counted" error="You don't have permission." />);
    expect(screen.getByText('Stock could not be counted')).toBeInTheDocument();
  });

  it('treats an empty error as a failure, not a denial', () => {
    // "" must not match the denial pattern by accident and claim the user is
    // unauthorised when we simply do not know what happened.
    render(<ErrorState error="" />);
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
  });
});
