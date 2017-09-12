-module(db_holiday).

-export([holidays_of_country/1,
         create/3,
         get_channel_holidays/2,
         set_channel_holidays/3,
         set_default_holidays/3,
         holiday_keys/0]).

%% needed so atoms exist.
holiday_keys () -> [country, date, name, user].

holidays_of_country(Country) ->
  Q = <<"SELECT name, date FROM holidays WHERE country = $1">>,
  db:query(Q, [Country]).

create(Country, Date, Name) ->
  Q = <<"INSERT INTO holidays(country, date, name) "
        "VALUES($1, $2, $3) RETURNING country, date, name">>,
  case db:query(Q, [Country, Date, Name]) of
    {ok, [Result | []]} -> {ok, Result};
    {error, unique_violation} -> {error, holiday_already_exists}
  end.

get_channel_holidays(Email, Channel) ->
  case db_channel:get_id(Email, Channel) of
    {ok, ChannelId} ->
      Q = <<"SELECT to_char(date, 'YYYY-MM-DD') as date, name FROM channel_holidays "
            "WHERE channel = $1 ORDER BY date ">>,
      db:query(Q, [ChannelId]);
    Error -> Error
  end.

set_default_holidays(Email, Channel, Country) ->
  case db_channel:get_id(Email, Channel) of
    {ok, ChannelId} ->
      Q = <<"INSERT INTO channel_holidays(channel, date, name) "
            "SELECT $1, date, name FROM holidays WHERE country = $2">>,
      db:query(Q, [ChannelId, Country]);
    Error -> Error
  end.

set_channel_holidays(Email, Channel, Holidays) ->
  case db_channel:get_id(Email, Channel) of
    {ok, ChannelId} ->
      Values = [io_lib:format(<<"(~p, '~s', '~s')">>, [ChannelId, Date, db:escape_string(Name)])
              || #{date := Date, name := Name} <- Holidays],
      Values2 = lists:join(<<", ">>, Values),

      Q = [<<"INSERT INTO channel_holidays(channel, date, name) VALUES ">>,
           Values2,
           <<" RETURNING date, name">>],
      Q2 = iolist_to_binary(Q),

      DeleteQ = <<"DELETE FROM channel_holidays WHERE channel = $1">>,
      case db:query(DeleteQ, [ChannelId]) of
        ok -> db:query(Q2, [])
      end;
    Error -> Error
  end.
