-module(slack_channel).

-export([handle/2]).

handle(Config, Message) ->
    Targets = maps:get(channels, Config),
    HookUrl = maps:get(url, Config),
    %% FIXME emoji and username should be default when blank
    BasePayload = #{
      text => Message,
      username => maps:get(username, Config, <<"Holiday Reminder">>),
      icon_emoji => maps:get(emoji, Config, <<":calendar:">>)
     },

    lists:foreach(
      fun (Target) ->
              Payload = BasePayload#{channel => Target},
              lager:debug("Sending request to slack: ~p", [Payload]),
              {ok, 200, _, _} = hp_request:post_json(HookUrl, Payload)
      end, Targets).
