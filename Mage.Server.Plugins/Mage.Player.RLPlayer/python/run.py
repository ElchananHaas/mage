import gym
env=gym.make('gym_xmage:learnerVsRandom-v0')
for games in range(5):
    observation = env.reset()
    for _ in range(10000):
        env.render()
        # your agent here (this takes random actions)
        action = env.sample_obs(observation)
        observation, reward, done, info = env.step(action)
        if done:
            env.render()
            break