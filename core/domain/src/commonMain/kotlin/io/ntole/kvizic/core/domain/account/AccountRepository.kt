package io.ntole.kvizic.core.domain.account

/**
 * The account of whoever plays on this device: a guest's, or one Play Games signed in. Implemented in
 * `:core:data`. There is no username or password: a guest is minted with no form, and Play Games is the
 * one way to carry an account to another device.
 */
public interface AccountRepository {
    /**
     * Logs this device out: tells the server, best effort, then drops the stored session whatever the
     * server answered, so the next call plays as a fresh guest, which settles who plays here. The
     * account's other devices stay signed in.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException only when the session could not be dropped.
     */
    public suspend fun logOut()

    /**
     * Deletes the account of the player this device plays as, for good, then drops the stored session, so
     * the next call plays as a fresh guest. A server that no longer knows the session, the account gone
     * already, counts as deleted. With no session stored there is nothing to delete, and nothing is sent.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any other failure, offline included, the
     *   stored session left as it was: nothing is forgotten until the server has said the account is gone.
     */
    public suspend fun deleteAccount()
}
