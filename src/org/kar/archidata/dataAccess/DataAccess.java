package org.kar.archidata.dataAccess;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.kar.archidata.GlobalConfiguration;
import org.kar.archidata.annotation.AnnotationTools;
import org.kar.archidata.dataAccess.options.Condition;
import org.kar.archidata.dataAccess.options.FilterValue;
import org.kar.archidata.dataAccess.options.Limit;
import org.kar.archidata.dataAccess.options.QueryOption;
import org.kar.archidata.dataAccess.options.TransmitKey;
import org.kar.archidata.db.DBConfig;
import org.kar.archidata.db.DBEntry;
import org.kar.archidata.db.DbInterface;
import org.kar.archidata.db.DbInterfaceMorphia;
import org.kar.archidata.db.DbInterfaceSQL;
import org.kar.archidata.exception.DataAccessException;
import org.kar.archidata.tools.UuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.InternalServerErrorException;

/* TODO list:
   - Manage to group of SQL action to permit to commit only at the end.
 */

/** Data access is an abstraction class that permit to access on the DB with a function wrapping that permit to minimize the SQL writing of SQL code. This interface support the SQL and SQLite
 * back-end. */
public abstract class DataAccess {
	final static Logger LOGGER = LoggerFactory.getLogger(DataAccess.class);

	public static final DataAccess createInterface() {
		try {
			return DataAccess.createInterface(GlobalConfiguration.getDbconfig());
		} catch (InternalServerErrorException | IOException e) {
			LOGGER.error("Fail to initialize connection of the DB");
			e.printStackTrace();
		}
		return null;
	}

	public static final DataAccess createInterface(final DBConfig config)
			throws InternalServerErrorException, IOException {
		final DBEntry entry = DBEntry.createInterface(config);
		return DataAccess.createInterface(entry.getDbInterface());
	}

	public static final DataAccess createInterface(final DbInterface io) throws InternalServerErrorException {
		if (io instanceof final DbInterfaceMorphia ioMorphia) {
			return new DataAccessMorphia(ioMorphia);
		} else if (io instanceof final DbInterfaceSQL ioSQL) {
			return new DataAccessSQL(ioSQL);
		}
		throw new InternalServerErrorException("unknow DB interface ... ");
	}

	public boolean isDBExist(final String name, final QueryOption... option) throws InternalServerErrorException {
		throw new InternalServerErrorException("Can Not manage the DB-access");
	}

	public boolean createDB(final String name) {
		throw new InternalServerErrorException("Can Not manage the DB-access");
	}

	public boolean isTableExist(final String name, final QueryOption... option) throws InternalServerErrorException {
		throw new InternalServerErrorException("Can Not manage the DB-access");
	}

	public <ID_TYPE> QueryCondition getTableIdCondition(final Class<?> clazz, final ID_TYPE idKey)
			throws DataAccessException {
		// Find the ID field type ....
		final Field idField = AnnotationTools.getIdField(clazz);
		if (idField == null) {
			throw new DataAccessException(
					"The class have no annotation @Id ==> can not determine the default type searching");
		}
		// check the compatibility of the id and the declared ID
		final Class<?> typeClass = idField.getType();
		if (idKey == null) {
			throw new DataAccessException("Try to identify the ID type and object wa null.");
		}
		if (idKey.getClass() != typeClass) {
			if (idKey.getClass() == Condition.class) {
				throw new DataAccessException(
						"Try to identify the ID type on a condition 'close' internal API error use xxxWhere(...) instead.");
			}
			throw new DataAccessException("Request update with the wrong type ...");
		}
		return new QueryCondition(AnnotationTools.getFieldName(idField), "=", idKey);
	}

	// TODO: manage insert batch...
	public <T> List<T> insertMultiple(final List<T> data, final QueryOption... options) throws Exception {
		final List<T> out = new ArrayList<>();
		for (final T elem : data) {
			final T tmp = insert(elem, options);
			out.add(tmp);
		}
		return out;
	}

	abstract public <T> T insert(final T data, final QueryOption... option) throws Exception;

	// seems a good idea, but very dangerous if we not filter input data... if set an id it can be complicated...
	public <T> T insertWithJson(final Class<T> clazz, final String jsonData) throws Exception {
		final ObjectMapper mapper = new ObjectMapper();
		// parse the object to be sure the data are valid:
		final T data = mapper.readValue(jsonData, clazz);
		return insert(data);
	}

	/** Update an object with the inserted json data
	 *
	 * @param <T> Type of the object to insert
	 * @param <ID_TYPE> Master key on the object manage with @Id
	 * @param clazz Class reference of the insertion model
	 * @param id Key to insert data
	 * @param jsonData Json data (partial) values to update
	 * @return the number of object updated
	 * @throws Exception */
	public <T, ID_TYPE> long updateWithJson(
			final Class<T> clazz,
			final ID_TYPE id,
			final String jsonData,
			final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(clazz, id)));
		options.add(new TransmitKey(id));
		return updateWhereWithJson(clazz, jsonData, options.getAllArray());
	}

	public <T> long updateWhereWithJson(final Class<T> clazz, final String jsonData, final QueryOption... option)
			throws Exception {
		final QueryOptions options = new QueryOptions(option);
		if (options.get(Condition.class).size() == 0) {
			throw new DataAccessException("request a updateWhereWithJson without any condition");
		}
		final ObjectMapper mapper = new ObjectMapper();
		// parse the object to be sure the data are valid:
		final T data = mapper.readValue(jsonData, clazz);
		// Read the tree to filter injection of data:
		final JsonNode root = mapper.readTree(jsonData);
		final List<String> keys = new ArrayList<>();
		final var iterator = root.fieldNames();
		iterator.forEachRemaining(e -> keys.add(e));
		options.add(new FilterValue(keys));
		return updateWhere(data, options.getAllArray());
	}

	public <T, ID_TYPE> long update(final T data, final ID_TYPE id) throws Exception {
		return update(data, id, AnnotationTools.getFieldsNames(data.getClass()));
	}

	/** @param <T>
	 * @param data
	 * @param id
	 * @param filterValue
	 * @return the affected rows.
	 * @throws Exception */
	public <T, ID_TYPE> long update(
			final T data,
			final ID_TYPE id,
			final List<String> updateColomn,
			final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(data.getClass(), id)));
		options.add(new FilterValue(updateColomn));
		options.add(new TransmitKey(id));
		return updateWhere(data, options);
	}

	public <T> long updateWhere(final T data, final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		return updateWhere(data, options);
	}

	public abstract <T> long updateWhere(final T data, QueryOptions options) throws Exception;

	public void addElement(final PreparedStatement ps, final Object value, final CountInOut iii) throws Exception {
		if (value instanceof final UUID tmp) {
			final byte[] dataByte = UuidUtils.asBytes(tmp);
			ps.setBytes(iii.value, dataByte);
		} else if (value instanceof final Long tmp) {
			this.LOGGER.debug("Inject Long => {}", tmp);
			ps.setLong(iii.value, tmp);
		} else if (value instanceof final Integer tmp) {
			this.LOGGER.debug("Inject Integer => {}", tmp);
			ps.setInt(iii.value, tmp);
		} else if (value instanceof final String tmp) {
			this.LOGGER.debug("Inject String => {}", tmp);
			ps.setString(iii.value, tmp);
		} else if (value instanceof final Short tmp) {
			this.LOGGER.debug("Inject Short => {}", tmp);
			ps.setShort(iii.value, tmp);
		} else if (value instanceof final Byte tmp) {
			this.LOGGER.debug("Inject Byte => {}", tmp);
			ps.setByte(iii.value, tmp);
		} else if (value instanceof final Float tmp) {
			this.LOGGER.debug("Inject Float => {}", tmp);
			ps.setFloat(iii.value, tmp);
		} else if (value instanceof final Double tmp) {
			this.LOGGER.debug("Inject Double => {}", tmp);
			ps.setDouble(iii.value, tmp);
		} else if (value instanceof final Boolean tmp) {
			this.LOGGER.debug("Inject Boolean => {}", tmp);
			ps.setBoolean(iii.value, tmp);
		} else if (value instanceof final Timestamp tmp) {
			this.LOGGER.debug("Inject Timestamp => {}", tmp);
			ps.setTimestamp(iii.value, tmp);
		} else if (value instanceof final Date tmp) {
			this.LOGGER.debug("Inject Date => {}", tmp);
			ps.setTimestamp(iii.value, java.sql.Timestamp.from((tmp).toInstant()));
		} else if (value instanceof final LocalDate tmp) {
			this.LOGGER.debug("Inject LocalDate => {}", tmp);
			ps.setDate(iii.value, java.sql.Date.valueOf(tmp));
		} else if (value instanceof final LocalTime tmp) {
			this.LOGGER.debug("Inject LocalTime => {}", tmp);
			ps.setTime(iii.value, java.sql.Time.valueOf(tmp));
		} else if (value.getClass().isEnum()) {
			this.LOGGER.debug("Inject ENUM => {}", value.toString());
			ps.setString(iii.value, value.toString());
		} else {
			throw new DataAccessException("Not manage type ==> need to add it ...");
		}
	}

	public <T> T getWhere(final Class<T> clazz, final QueryOptions options) throws Exception {
		options.add(new Limit(1));
		final List<T> values = getsWhere(clazz, options);
		if (values.size() == 0) {
			return null;
		}
		return values.get(0);
	}

	public <T> T getWhere(final Class<T> clazz, final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		return getWhere(clazz, options);
	}

	public <T> List<T> getsWhere(final Class<T> clazz, final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		return getsWhere(clazz, options);
	}

	public Condition conditionFusionOrEmpty(final QueryOptions options, final boolean throwIfEmpty)
			throws DataAccessException {
		if (options == null) {
			return new Condition();
		}
		final List<Condition> conditions = options.get(Condition.class);
		if (conditions.size() == 0) {
			if (throwIfEmpty) {
				throw new DataAccessException("request a gets without any condition");
			} else {
				return new Condition();
			}
		}
		Condition condition = null;
		if (conditions.size() == 1) {
			condition = conditions.get(0);
		} else {
			final QueryAnd andCondition = new QueryAnd();
			for (final Condition cond : conditions) {
				andCondition.add(cond.condition);
			}
			condition = new Condition(andCondition);
		}
		return condition;
	}

	abstract public <T> List<T> getsWhere(final Class<T> clazz, final QueryOptions options)
			throws DataAccessException, IOException;

	public <ID_TYPE> long count(final Class<?> clazz, final ID_TYPE id, final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(clazz, id)));
		return countWhere(clazz, options);
	}

	public long countWhere(final Class<?> clazz, final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		return countWhere(clazz, options);
	}

	public abstract long countWhere(final Class<?> clazz, final QueryOptions options) throws Exception;

	public <T, ID_TYPE> T get(final Class<T> clazz, final ID_TYPE id, final QueryOption... option) throws Exception {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(clazz, id)));
		return getWhere(clazz, options.getAllArray());
	}

	public <T> List<T> gets(final Class<T> clazz) throws Exception {
		return getsWhere(clazz);
	}

	public <T> List<T> gets(final Class<T> clazz, final QueryOption... option) throws Exception {
		return getsWhere(clazz, option);
	}

	/** Delete items with the specific Id (cf @Id) and some options. If the Entity is manage as a softDeleted model, then it is flag as removed (if not already done before).
	 * @param <ID_TYPE> Type of the reference @Id
	 * @param clazz Data model that might remove element
	 * @param id Unique Id of the model
	 * @param options (Optional) Options of the request
	 * @return Number of element that is removed. */
	public <ID_TYPE> long delete(final Class<?> clazz, final ID_TYPE id, final QueryOption... options)
			throws Exception {
		final String hasDeletedFieldName = AnnotationTools.getDeletedFieldName(clazz);
		if (hasDeletedFieldName != null) {
			return deleteSoft(clazz, id, options);
		} else {
			return deleteHard(clazz, id, options);
		}
	}

	/** Delete items with the specific condition and some options. If the Entity is manage as a softDeleted model, then it is flag as removed (if not already done before).
	 * @param clazz Data model that might remove element.
	 * @param condition Condition to remove elements.
	 * @param options (Optional) Options of the request.
	 * @return Number of element that is removed. */
	public long deleteWhere(final Class<?> clazz, final QueryOption... option) throws Exception {
		final String hasDeletedFieldName = AnnotationTools.getDeletedFieldName(clazz);
		if (hasDeletedFieldName != null) {
			return deleteSoftWhere(clazz, option);
		} else {
			return deleteHardWhere(clazz, option);
		}
	}

	public <ID_TYPE> long deleteHard(final Class<?> clazz, final ID_TYPE id, final QueryOption... option)
			throws Exception {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(clazz, id)));
		return deleteHardWhere(clazz, options.getAllArray());
	}

	public abstract long deleteHardWhere(final Class<?> clazz, final QueryOption... option) throws Exception;

	private <ID_TYPE> long deleteSoft(final Class<?> clazz, final ID_TYPE id, final QueryOption... option)
			throws Exception {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(clazz, id)));
		return deleteSoftWhere(clazz, options.getAllArray());
	}

	public abstract long deleteSoftWhere(final Class<?> clazz, final QueryOption... option) throws Exception;

	public <ID_TYPE> long unsetDelete(final Class<?> clazz, final ID_TYPE id) throws DataAccessException {
		return unsetDeleteWhere(clazz, new Condition(getTableIdCondition(clazz, id)));
	}

	public <ID_TYPE> long unsetDelete(final Class<?> clazz, final ID_TYPE id, final QueryOption... option)
			throws DataAccessException {
		final QueryOptions options = new QueryOptions(option);
		options.add(new Condition(getTableIdCondition(clazz, id)));
		return unsetDeleteWhere(clazz, options.getAllArray());
	}

	public abstract long unsetDeleteWhere(final Class<?> clazz, final QueryOption... option) throws DataAccessException;

	public abstract void drop(final Class<?> clazz, final QueryOption... option) throws Exception;

	public abstract void cleanAll(final Class<?> clazz, final QueryOption... option) throws Exception;

}
