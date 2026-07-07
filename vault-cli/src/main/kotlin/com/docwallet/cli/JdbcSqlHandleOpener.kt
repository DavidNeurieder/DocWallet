package com.docwallet.cli

import com.docwallet.vault.database.SqlHandleOpener
import java.io.File

class JdbcSqlHandleOpener : SqlHandleOpener {
    override fun open(path: String): SqlHandleJdbc {
        return SqlHandleJdbc.open(path)
    }

    override fun openInMemory(): SqlHandleJdbc {
        return SqlHandleJdbc.openInMemory()
    }

    override fun delete(path: String) {
        File(path).delete()
    }
}
