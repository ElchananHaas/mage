import gym
import torch
from torch import nn, scalar_tensor
import torch.nn.functional as F
import numpy as np
env=gym.make('gym_xmage:learnerVsRandom-v0')
from hparams import hparams
class Representer():
    def __init__(self):
        self.names_to_ints=dict()
        self.count=1
    #Return list of IDs, list of action IDs 
    def convert(self,obs):
        game=obs['game']
        reals=game['reals']
        flat_reals=self.flatten_reals(reals)
        perms=game['permanents']
        (cardIDs,card_nums)=self.id_perms(perms)
        actionIDs=self.id_actions(obs['actions'])
        action_mask=[1]*len(obs['actions'])
        result={
            "reals":flat_reals,
            "cardIDs":cardIDs,
            "card_nums":card_nums,
            "actionIDs":actionIDs,
            "action_mask":action_mask,
        }
        #print(result)
        return result
    def get_num(self,cardString):
        if(cardString in self.names_to_ints):
            return self.names_to_ints[cardString]
        else:
            self.count+=1
            self.names_to_ints[cardString]=self.count
            return self.count
    def id_actions(self,actions):
        L=[]
        max_actions=2
        for act in actions:
            key_count=0
            act_repr=[]
            for key in act:
                key_count+=1
                ID=self.get_num("Action-"+key+str(act[key]))
                act_repr.append(ID)
            for i in range(max_actions-key_count):
                act_repr.append(self.get_num("Blank Action"))
            L.append(act_repr)
        return L
    def id_perms(self,perms):
        id_L=[]
        reals_L=[]
        for obj in perms:
            card_string=obj['controller']+"-"+obj['name']
            embedID=self.get_num(card_string)
            id_L.append(embedID)
            card_nums=(obj['power'],obj['toughness'])
            reals_L.append(card_nums)
        return (id_L,reals_L)
    def flatten_reals(self,reals):
        L=[]
        for val in reals.values():
            if(type(val)==int):
                L.append(val)
            elif(type(val)==dict):
                L.extend(self.flatten_reals(val))
            else:
                return 1/0
        return L
def pad_to_numpy(arr):
    if(len(arr)==0):
        return arr
    numpied=[np.array(val) for val in arr]
    shapes=[val.shape for val in numpied if val.shape!=(0,)]
    #print(shapes)
    if(len(shapes)==0):
        max_size=(0,)
    else:
        max_size=np.maximum.reduce(shapes)
    for i in range(len(arr)):
        numpied[i].resize(max_size)
    ret=np.array(numpied)
    return ret
def pad_dicts(dicts):
    for key in dicts:
        dicts[key]=pad_to_numpy(dicts[key])
#Combines a list of dicts with the same keys into a dict of lists
def merge_dicts(dicts):
    res={}
    for key in dicts[0]:
        L=[]
        for dictionary in dicts:
            L.append(dictionary[key])
        res[key]=L
    pad_dicts(res)
    return res
class Preparer(nn.Module):
    def __init__(self,hparams):
        super().__init__()
        self.embed=nn.Embedding(hparams['max_represent'],hparams['embed_dim'])
        self.hparams=hparams
    def forward(self,data):
        data=merge_dicts(data)
        reals=torch.tensor(data['reals'],dtype=torch.float32)
        card_embed=self.embed(torch.tensor(data['cardIDs'],dtype=torch.long))
        card_nums=torch.tensor(data['card_nums'],dtype=torch.float32)
        if(card_nums.shape[1]==0):
            card_nums=torch.reshape(card_nums,[*card_nums.shape,hparams['num_card_reals']])
        card_all=torch.cat([card_embed,card_nums],dim=2)
        action_embed=self.embed(torch.tensor(data['actionIDs'],dtype=torch.long))
        shape=action_embed.shape
        action_embed=action_embed.reshape(shape[0],shape[1],shape[2]*shape[3])
        return (reals,card_all,action_embed,data['action_mask'])
class InputNorm(nn.Module):
    def __init__(self,hparams):
        super().__init__()
        self.num_reals=hparams["num_reals"]
        self.avg=torch.zeros((1,self.num_reals))
        self.variance=torch.ones((1,self.num_reals))
        self.iterations=1
        self.max_iter=hparams['decay_steps']
    def forward(self,x):
        if not self.training: #Only fit parameters when exploring
            self.iterations=min(self.max_iter,self.iterations+1)
            mult=1/self.iterations
            detached_x=x.detach()
            self.avg=(1-mult)*self.avg+mult*detached_x
            squared_diff=(detached_x-self.avg)**2
            mult=1/(self.iterations+1)
            self.variance=(1-mult)*self.variance+mult*squared_diff
        x=x-self.avg
        x=x/torch.sqrt(self.variance)
        return x
class LinNet(nn.Module):
    def __init__(self,hparams):
        super().__init__()
        self.hparams=hparams
        act_dim=hparams["num_card_reals"]*hparams[ "embed_dim"]
        self.act_linear=nn.Linear(act_dim,hparams["dot_dim"])
        card_dim=hparams[ "embed_dim"]+hparams["num_card_reals"]
        self.cards_linear=nn.Linear(card_dim,hparams["dot_dim"])
        self.reals_linear=nn.Linear(hparams["num_reals"],hparams["dot_dim"])
        self.norm=InputNorm(hparams)
    def forward(self,reals,card_all,action_embed,action_mask):
        reals=self.norm(reals)
        reals=self.reals_linear(reals)
        if(card_all.shape[1]>0):
            cards=torch.mean(card_all,dim=1)
            cards=self.cards_linear(cards)
            reals=reals+cards
        actions=self.act_linear(action_embed)
        reals=torch.unsqueeze(reals,dim=2)
        pred_values=actions@reals
        pred_values=torch.squeeze(pred_values,dim=2)
        subtractor=1000*(1-action_mask)
        subtractor=torch.tensor(subtractor)
        pred_values=pred_values-subtractor
        pred_values=F.log_softmax(pred_values,dim=1)
        return pred_values

class ValueNet(nn.Module):
    def __init__(self,hparams):
        super().__init__()
        self.hparams=hparams
        act_dim=hparams["num_card_reals"]*hparams[ "embed_dim"]
        self.act_linear=nn.Linear(act_dim,hparams["dot_dim"])
        card_dim=hparams[ "embed_dim"]+hparams["num_card_reals"]
        self.cards_linear=nn.Linear(card_dim,hparams["dot_dim"])
        self.reals_linear=nn.Linear(hparams["num_reals"],hparams["dot_dim"])
        self.lin_two=nn.Linear(hparams["dot_dim"],hparams["dot_dim"])
        self.out_lin=nn.Linear(hparams["dot_dim"],1)
        self.norm=InputNorm(hparams)
    def forward(self,reals,card_all,action_embed):
        reals=self.norm(reals)
        reals=self.reals_linear(reals)
        if(card_all.shape[1]>0):
            cards=torch.mean(card_all,dim=1)
            cards=self.cards_linear(cards)
            reals=reals+cards
        actions=self.act_linear(action_embed)
        actions=torch.mean(actions,dim=1)
        reals=reals+actions
        reals=reals+F.relu(self.lin_two(reals))
        last_one=self.out_lin(reals)
        return torch.tanh(last_one)

class MainNet(nn.Module):
    def __init__(self,hparams):
        super().__init__()
        self.hparams=hparams  
        self.prep=Preparer(hparams)
        self.net=LinNet(hparams)   
        self.value_net=ValueNet(hparams)   
    def forward(self, converted):
        (reals,card_all,action_embed,action_mask)=self.prep(converted)
        actions=self.net(reals,card_all,action_embed,action_mask) 
        values=self.value_net(reals,card_all,action_embed) 
        return (actions,values)
converter=Representer() 
net=MainNet(hparams)
optimizer = torch.optim.Adam(net.parameters(), lr=hparams['lr'])

def play_game():
    observation = env.reset()
    actions=[]
    converted=converter.convert(observation)
    observations=[converted]
    rewards=[]
    while(True):
        net.eval()
        # your agent here (this takes random actions) 
        log_actions=net([converted])[0]
        log_actions=torch.squeeze(log_actions).detach()
        #print(log_actions.shape)
        if(games%50==0):
            print(torch.exp(log_actions))
        sample=torch.multinomial(torch.exp(log_actions),num_samples=1)
        #print("sample is",sample)
        action =int(sample[0])
        #print("action is",action)
        actions.append(action)
        observation, reward, done, info = env.step(action)
        rewards.append(reward)
        converted=converter.convert(observation)
        if(len(converted['actionIDs'])>0):
            observations.append(converted)
        else:
            break
    for i in range(len(rewards)):
        rewards[i]=rewards[-1]
    return actions,observations,rewards
for games in range(3000):
    (actions,observations,rewards)=play_game()
    print(rewards[-1])
    net.train()
    optimizer.zero_grad()
    (out,critic)=net(observations)
    max_action_len=max([len(obs['action_mask']) for obs in observations])
    back_grad=torch.zeros((len(observations),max_action_len))
    entropy=torch.mean(out*torch.exp(out),dim=1,keepdim=True)*hparams["entropy_weight"]
    #Use neagive entropy becasue I want to increase it
    scalar_loss=entropy
    end_reward=rewards[-1] #For now, just use eposide rewards
    for i in range(len(actions)):
        back_grad[i,actions[i]]=-(rewards[i]-critic[i,0].detach()) #negate end reward to IMPROVE agent!
    critic_error=(torch.tensor(rewards).unsqueeze(dim=1)-critic)**2
    if(games%50==0):
        print(critic_error)
    scalar_loss=scalar_loss+critic_error
    out=torch.cat([out,scalar_loss],dim=1)
    back_grad=torch.cat([back_grad,torch.ones_like(scalar_loss)],dim=1)
    if(back_grad.shape[0]>hparams['batch_cutoff']):
        back_grad=back_grad*hparams['batch_cutoff']/back_grad.shape[0]
    out.backward(back_grad)
    #print(out.shape,back_grad.shape)
    optimizer.step()