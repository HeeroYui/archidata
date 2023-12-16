package org.kar.archidata.dataAccess;

import java.sql.PreparedStatement;
import java.util.List;

public class QueryOr implements QueryItem {
	protected final List<QueryItem> childs;

	public QueryOr(final List<QueryItem> childs) {
		this.childs = childs;
	}

	public QueryOr(final QueryItem... childs) {
		this.childs = List.of(childs);
	}

	@Override
	public void generateQuerry(final StringBuilder query, final String tableName) {
		if (this.childs.size() >= 1) {
			query.append(" (");
		}
		boolean first = true;
		for (final QueryItem elem : this.childs) {
			if (first) {
				first = false;
			} else {
				query.append(" OR ");
			}
			elem.generateQuerry(query, tableName);
		}
		if (this.childs.size() >= 1) {
			query.append(")");
		}
	}

	@Override
	public void injectQuerry(final PreparedStatement ps, final CountInOut iii) throws Exception {
		for (final QueryItem elem : this.childs) {
			elem.injectQuerry(ps, iii);
		}
	}
}
